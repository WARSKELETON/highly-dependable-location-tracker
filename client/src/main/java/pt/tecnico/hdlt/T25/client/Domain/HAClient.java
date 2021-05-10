package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.LocationServerServiceGrpc;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.grpc.Status.DEADLINE_EXCEEDED;
import static pt.tecnico.hdlt.T25.crypto.Crypto.getPriv;

public class HAClient extends AbstractClient {
    private static final String OBTAIN_LOCATION_REPORT = "obtainLocation";
    private static final String OBTAIN_USERS_AT_LOCATION = "obtainUsers";

    public HAClient(String serverHost, int serverPort, int clientId, SystemInfo systemInfo, boolean isTest, int maxByzantineUsers, int maxReplicas, int maxByzantineReplicas) throws GeneralSecurityException, JsonProcessingException, InterruptedException {
        super(serverHost, serverPort, clientId, systemInfo, maxByzantineUsers, maxReplicas, maxByzantineReplicas);
        this.setPrivateKey(getPriv("ha-priv.key"));
        checkLatestSeqNumberRegular();
        if (!isTest) this.eventLoop();
    }

    public LocationServer.ObtainUsersAtLocationRequest buildObtainUsersAtLocationRequest(int latitude, int longitude, int ep, int currentSeqNumber, int serverId) throws GeneralSecurityException {
        Location usersLocationRequest = new Location(-1, ep, latitude, longitude);
        String requestContent = usersLocationRequest.toJsonString();
        String signatureString = requestContent + currentSeqNumber;

        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        return LocationServer.ObtainUsersAtLocationRequest.newBuilder()
                .setKey(Crypto.encryptRSA(Base64.getEncoder().encodeToString(encodedKey), this.getServerPublicKey(serverId)))
                .setContent(Crypto.encryptAES(secretKeySpec, requestContent))
                .setSignature(Crypto.sign(signatureString, this.getPrivateKey()))
                .setSeqNumber(Crypto.encryptAES(secretKeySpec, String.valueOf(currentSeqNumber)))
                .build();
    }

    public List<Location> obtainUsersAtLocationRegular(int latitude, int longitude, int ep) throws GeneralSecurityException, InterruptedException {
        List<Location> locations = new ArrayList<>();

        final CountDownLatch finishLatch = new CountDownLatch((getMaxReplicas() + getMaxByzantineReplicas()) / 2 + 1);

        Consumer<LocationServer.ObtainUsersAtLocationResponse> requestObserver = new Consumer<>() {
            @Override
            public void accept(LocationServer.ObtainUsersAtLocationResponse response)  {
                synchronized (finishLatch) {
                    if (finishLatch.getCount() == 0) return;

                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        for (LocationServer.ObtainLocationReportResponse report : response.getLocationReportsList()) {
                            // TODO Fix verify for users
                            if (verifyLocationReport(0, 0, report)) {
                                SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(report.getKey(), getPrivateKey());
                                String locationProverContent = Crypto.decryptAES(secretKeySpec, report.getLocationProver().getContent());
                                locations.add(objectMapper.readValue(locationProverContent, Location.class));
                                finishLatch.countDown();
                            }
                        }
                    } catch (JsonProcessingException|GeneralSecurityException ex) {
                        System.err.println("user" + getClientId() + ": caught processing exception while obtaining users at location");
                    }
                }
            }
        };

        for (int serverId : getLocationServerServiceStubs().keySet()) {
            LocationServer.ObtainUsersAtLocationRequest request = buildObtainUsersAtLocationRequest(latitude, longitude, ep, this.getSeqNumbers().get(serverId), serverId);
            obtainUsersAtLocation(getLocationServerServiceStubs().get(serverId), request, requestObserver);
        }

        finishLatch.await(10, TimeUnit.SECONDS);

        return locations;
    }

    public void obtainUsersAtLocation(LocationServerServiceGrpc.LocationServerServiceStub locationServerServiceStub, LocationServer.ObtainUsersAtLocationRequest request, Consumer<LocationServer.ObtainUsersAtLocationResponse> callback) {
        try {
            locationServerServiceStub.withDeadlineAfter(1, TimeUnit.SECONDS).obtainUsersAtLocation(request, new StreamObserver<>() {
                @Override
                public void onNext(LocationServer.ObtainUsersAtLocationResponse response) {
                    callback.accept(response);
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onCompleted() {
                }
            });
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode().equals(DEADLINE_EXCEEDED.getCode())) {
                System.out.println("user" + getClientId() + ": TIMEOUT CLIENT");
            } else {
                throw e;
            }
        }
    }

    @Override
    void parseCommand(String cmd) throws GeneralSecurityException, JsonProcessingException {

        String[] args = cmd.split(" ");

        if (args[0].equals(OBTAIN_LOCATION_REPORT)) {
            if (args.length != 3) {
                return;
            }
            int userId = Integer.parseInt(args[1]);
            int ep = Integer.parseInt(args[2]);

            try {
                this.obtainLocationReportAtomic(userId, ep);
            } catch (StatusRuntimeException | InterruptedException ex2) {
                System.err.println(ex2.getMessage());
            }
        }

        else if (args[0].equals(OBTAIN_USERS_AT_LOCATION)) {
            if (args.length != 4) {
                return;
            }
            int latitude = Integer.parseInt(args[1]);
            int longitude = Integer.parseInt(args[2]);
            int ep = Integer.parseInt(args[3]);

            try {
                this.obtainUsersAtLocationRegular(latitude, longitude, ep);
            } catch (StatusRuntimeException | InterruptedException ex2) {
                System.err.println(ex2.getMessage());
            }
        }
    }
}
