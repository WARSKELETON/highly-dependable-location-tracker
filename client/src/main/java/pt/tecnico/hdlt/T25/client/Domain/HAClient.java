package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.StatusRuntimeException;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.grpc.Status.DEADLINE_EXCEEDED;
import static pt.tecnico.hdlt.T25.crypto.Crypto.getPriv;

public class HAClient extends AbstractClient {
    private static final String OBTAIN_LOCATION_REPORT = "obtainLocation";
    private static final String OBTAIN_USERS_AT_LOCATION = "obtainUsers";

    public HAClient(String serverHost, int serverPort, int clientId, SystemInfo systemInfo, boolean isTest) throws GeneralSecurityException, JsonProcessingException {
        super(serverHost, serverPort, clientId, systemInfo);
        this.setPrivateKey(getPriv("ha-priv.key"));
        checkLatestSeqNumber();
        System.out.println("Seq number: " + getSeqNumber());
        if (!isTest) this.eventLoop();
    }

    public List<Location> obtainUsersAtLocation(int latitude, int longitude, int ep) throws JsonProcessingException, GeneralSecurityException {
        int currentSeqNumber;
        synchronized (getSeqNumberLock()) {
            currentSeqNumber = this.getSeqNumber();
            setSeqNumber(currentSeqNumber + 1);
        }

        Location usersLocationRequest = new Location(-1, ep, latitude, longitude);
        String requestContent = usersLocationRequest.toJsonString();
        String signatureString = requestContent + currentSeqNumber;

        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        LocationServer.ObtainUsersAtLocationRequest request = LocationServer.ObtainUsersAtLocationRequest.newBuilder()
                .setKey(Crypto.encryptRSA(Base64.getEncoder().encodeToString(encodedKey), this.getServerPublicKey()))
                .setContent(Crypto.encryptAES(secretKeySpec, requestContent))
                .setSignature(Crypto.sign(signatureString, this.getPrivateKey()))
                .setSeqNumber(Crypto.encryptAES(secretKeySpec, String.valueOf(currentSeqNumber)))
                .build();

        List<LocationServer.ObtainLocationReportResponse> reports;
        while (true) {
            try {
                reports = this.getLocationServerServiceStub().withDeadlineAfter(1, TimeUnit.SECONDS).obtainUsersAtLocation(request).getLocationReportsList();
                break;
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode().equals(DEADLINE_EXCEEDED.getCode())) {
                    System.out.println("user" + getClientId() + ": TIMEOUT CLIENT");
                } else {
                    throw e;
                }
            }
        }

        List<Location> locations = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        for (LocationServer.ObtainLocationReportResponse report : reports) {
            if (verifyLocationReport(report, currentSeqNumber)) {
                secretKeySpec = Crypto.decryptKeyWithRSA(report.getKey(), this.getPrivateKey());
                String locationProverContent = Crypto.decryptAES(secretKeySpec, report.getLocationProver().getContent());
                locations.add(objectMapper.readValue(locationProverContent, Location.class));
            }
        }

        return locations;
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
                this.obtainLocationReport(userId, ep);
            } catch (StatusRuntimeException ex2) {
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
                this.obtainUsersAtLocation(latitude, longitude, ep);
            } catch (StatusRuntimeException ex2) {
                System.err.println(ex2.getMessage());
            }
        }
    }
}
