package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.LocationServerServiceGrpc;
import pt.tecnico.hdlt.T25.Proximity;
import pt.tecnico.hdlt.T25.ProximityServiceGrpc;
import pt.tecnico.hdlt.T25.client.Services.ProximityServiceImpl;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.grpc.Status.DEADLINE_EXCEEDED;
import static pt.tecnico.hdlt.T25.crypto.Crypto.getPriv;

public class Client extends AbstractClient {
    static final String LOCATION_PROOF_REQUEST = "proof";
    static final String SUBMIT_LOCATION_REPORT = "submit";
    static final String OBTAIN_LOCATION_REPORT = "obtain";
    private static final int CLIENT_ORIGINAL_PORT = 8000;

    private final int maxNearbyByzantineUsers;
    private Map<Integer, ProximityServiceGrpc.ProximityServiceStub> proximityServiceStubs;
    private Map<Integer, LocationReport> locationReports;

    public Client(String serverHost, int serverPort, int clientId, SystemInfo systemInfo, int maxByzantineUsers, int maxNearbyByzantineUsers, boolean isTest, int maxReplicas, int maxByzantineReplicas) throws IOException, GeneralSecurityException, InterruptedException {
        super(serverHost, serverPort, clientId, systemInfo, maxByzantineUsers, maxReplicas, maxByzantineReplicas);
        this.maxNearbyByzantineUsers = maxNearbyByzantineUsers;
        this.locationReports = new HashMap<>();
        this.proximityServiceStubs = new HashMap<>();
        this.connectToClients();
        this.setPrivateKey(getPriv("client" + clientId + "-priv.key"));
        checkLatestSeqNumberRegular();
        if (!isTest) this.eventLoop();
    }

    public int getMaxNearbyByzantineUsers() {
        return maxNearbyByzantineUsers;
    }

    public Map<Integer, ProximityServiceGrpc.ProximityServiceStub> getProximityServiceStubs() {
        return proximityServiceStubs;
    }

    public Map<Integer, LocationReport> getLocationReports() {
        return locationReports;
    }

    private void connectToClients() throws IOException {
        int numberOfUsers = this.getSystemInfo().getNumberOfUsers();

        // Open "Server" for other Clients
        final BindableService proximityService = new ProximityServiceImpl(this);
        io.grpc.Server proximityServer = ServerBuilder.forPort(CLIENT_ORIGINAL_PORT + this.getClientId()).addService(proximityService).build();
        proximityServer.start();

        // Connect to all other Clients
        for (int i = 0; i < numberOfUsers; i++) {
            if (i == this.getClientId()) continue;

            int port = CLIENT_ORIGINAL_PORT + i;
            String target = "localhost:" + port;
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            ProximityServiceGrpc.ProximityServiceStub stub = ProximityServiceGrpc.newStub(channel);
            proximityServiceStubs.put(i, stub);
        }
    }

    public List<Integer> getNearbyUsers(Location location) {
        int ep = location.getEp();
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        return this.getSystemInfo().getGrid().stream()
                .filter(location1 -> location1.getEp() == ep && location1.getUserId() != this.getClientId() && isNearby(latitude, longitude, location1.getLatitude(), location1.getLongitude()))
                .map(Location::getUserId)
                .collect(Collectors.toList());
    }

    boolean verifyLocationProofResponse(LocationProof originalLocationProof, String content) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            LocationProof locationProof = objectMapper.readValue(content, LocationProof.class);

            return locationProof.getEp() == originalLocationProof.getEp() &&
                    locationProof.getUserId() == originalLocationProof.getUserId() &&
                    locationProof.getLatitude() == originalLocationProof.getLatitude() &&
                    locationProof.getLongitude() == originalLocationProof.getLongitude() &&
                    locationProof.getWitnessId() == originalLocationProof.getWitnessId();
        } catch (JsonProcessingException ex) {
            System.err.println("Failed to process JSON in proof response.");
        }
        return false;
    }

    boolean createLocationReport(int clientId, int ep, int latitude, int longitude) throws InterruptedException {
        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();
        Location location = getMyLocation(ep);

        List<Integer> nearbyUsers = getNearbyUsers(location);

        final CountDownLatch finishLatch = new CountDownLatch(this.getMaxByzantineUsers());

        System.out.println("user" + getClientId() + ": Nearby users: " + nearbyUsers.size());
        for (int witnessId : nearbyUsers) {
            System.out.println("user" + getClientId() + ": Sending Location Proof Request to user" + witnessId + "...");

            LocationProof locationProof = new LocationProof(location.getUserId(), location.getEp(), location.getLatitude(), location.getLongitude(), witnessId);

            Consumer<Proximity.LocationProofResponse> requestObserver = new Consumer<>() {
                @Override
                public void accept(Proximity.LocationProofResponse response) {
                    synchronized (finishLatch) {
                        if (finishLatch.getCount() == 0) return;

                        if (Crypto.verify(response.getContent(), response.getSignature(), getUserPublicKey(witnessId)) && verifyLocationProofResponse(locationProof, response.getContent())) {
                            locationProofsContent.put(witnessId, response.getContent());
                            locationProofsSignatures.put(witnessId, response.getSignature());
                            System.out.println("user" + getClientId() + ": Received legitimate proof from user" + witnessId);
                            finishLatch.countDown();
                        } else {
                            System.out.println("user" + getClientId() + ": Received illegitimate proof from user" + witnessId);
                        }
                    }
                }
            };

            this.requestLocationProof(locationProof, witnessId, requestObserver);
        }

        finishLatch.await(10, TimeUnit.SECONDS);

        if (finishLatch.getCount() == 0) {
            String signature = location.toJsonString() + locationProofsContent.values().stream().reduce("", String::concat);
            LocationReport locationReport = new LocationReport(location, Crypto.sign(signature, this.getPrivateKey()), locationProofsContent, locationProofsSignatures);
            locationReports.put(ep, locationReport);
            return true;
        } else if (finishLatch.getCount() > 0 && nearbyUsers.size() >= getMaxByzantineUsers() + maxNearbyByzantineUsers) {
            return createLocationReport(clientId, ep, latitude, longitude);
        }

        return false;
    }

    Proximity.LocationProofRequest buildLocationProofRequest(LocationProof locationProof) {
        String content = locationProof.toJsonString();

        return Proximity.LocationProofRequest.newBuilder()
                .setContent(content)
                .setSignature(Crypto.sign(content, this.getPrivateKey()))
                .build();
    }

    void requestLocationProof(LocationProof locationProof, int witnessId, Consumer<Proximity.LocationProofResponse> callback) {
        Proximity.LocationProofRequest request = buildLocationProofRequest(locationProof);

        try {
            proximityServiceStubs.get(witnessId).withDeadlineAfter(1, TimeUnit.SECONDS).requestLocationProof(request, new StreamObserver<>() {
                @Override
                public void onNext(Proximity.LocationProofResponse response) {
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
                System.out.println("user" + getClientId() + ": TIMEOUT user" + witnessId);
                requestLocationProof(locationProof, witnessId, callback);
            } else {
                throw e;
            }
        }
    }

    public LocationServer.SubmitLocationReportRequest buildSubmitLocationReportRequest(int ep, int serverId) throws InterruptedException, GeneralSecurityException {
        LocationReport locationReport = locationReports.get(ep);
        Location myLocation = this.getMyLocation(ep);

        if (locationReport == null) {
            // TODO returning null
            if (!createLocationReport(getClientId(), ep, myLocation.getLatitude(), myLocation.getLongitude())) return null;
            locationReport = locationReports.get(ep);
        }

        SubmitLocationReportRequestHeader header = new SubmitLocationReportRequestHeader(this.getClientId(), serverId, "proofOfWork");
        String headerString = header.toJsonString();
        List<LocationServer.LocationMessage> locationProofMessages = new ArrayList<>();
        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        for (Integer witnessId : locationReport.getLocationProofsContent().keySet()) {
            locationProofMessages.add(
                    LocationServer.LocationMessage.newBuilder()
                            .setContent(Crypto.encryptAES(secretKeySpec, locationReport.getLocationProofsContent().get(witnessId)))
                            .setSignature(locationReport.getLocationProofsSignature().get(witnessId))
                            .build()
            );
        }

        String content = locationReport.getLocationProver().toJsonString();
        String requestSignatureContent = content + locationReport.getLocationProofsContent().values().stream().reduce("", String::concat) + secretKeySpec.toString() + headerString;
        return LocationServer.SubmitLocationReportRequest.newBuilder()
                .setKey(Crypto.encryptRSA(Base64.getEncoder().encodeToString(encodedKey), this.getServerPublicKey(serverId)))
                .setLocationProver(
                        LocationServer.LocationMessage.newBuilder()
                                .setContent(Crypto.encryptAES(secretKeySpec, content))
                                .setSignature(locationReport.getLocationProverSignature())
                                .build())
                .addAllLocationProofs(locationProofMessages)
                .setHeader(Crypto.encryptAES(secretKeySpec, headerString))
                .setRequestSignature(Crypto.sign(requestSignatureContent, this.getPrivateKey()))
                .build();
    }

    public void submitLocationReportAtomic(int ep) throws InterruptedException, GeneralSecurityException {
        final CountDownLatch finishLatch = new CountDownLatch((getMaxReplicas() + getMaxByzantineReplicas()) / 2 + 1);

        Consumer<LocationServer.SubmitLocationReportResponse> requestOnSuccessObserver = new Consumer<>() {
            @Override
            public void accept(LocationServer.SubmitLocationReportResponse response)  {
                synchronized (finishLatch) {
                    if (finishLatch.getCount() == 0) return;

                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), getPrivateKey());
                        String responseString = Crypto.decryptAES(secretKeySpec, response.getContent());
                        SubmitLocationReportResponse submitResponse = objectMapper.readValue(responseString, SubmitLocationReportResponse.class);
                        String signatureString = responseString + secretKeySpec.toString();

                        if (Crypto.verify(signatureString, response.getSignature(), getServerPublicKey(submitResponse.getServerId())) && submitResponse.isOk()) {
                            System.out.println("user" + getClientId() + ": Received submit location report response -> " + submitResponse.isOk());
                            finishLatch.countDown();
                        } else if (Crypto.verify(signatureString, response.getSignature(), getServerPublicKey(submitResponse.getServerId())) && !submitResponse.isOk()) {
                            System.out.println("user" + getClientId() + ": Received submit location report not ok response -> " + submitResponse.isOk());
                        }
                    } catch (JsonProcessingException|GeneralSecurityException ex) {
                        System.err.println("user" + getClientId() + ": caught processing exception while submitting report");
                    }
                }
            }
        };

        Consumer<Throwable> requestOnErrorObserver = new Consumer<>() {
            @Override
            public void accept(Throwable throwable) {
                synchronized (finishLatch) {
                    if (finishLatch.getCount() == 0) return;

                    if (throwable.getMessage().contains("ALREADY_EXISTS")) {
                        System.out.println("user" + getClientId() + ": Duplicate report!");
                        finishLatch.countDown();
                    }
                }
            }
        };

        for (int serverId : getLocationServerServiceStub().keySet()) {
            LocationServer.SubmitLocationReportRequest request = buildSubmitLocationReportRequest(ep, serverId);
            submitLocationReport(getLocationServerServiceStub().get(serverId), request, requestOnSuccessObserver, requestOnErrorObserver);
        }

        finishLatch.await(10, TimeUnit.SECONDS);

        if (finishLatch.getCount() == 0) {
            System.out.println("user" + getClientId() + ": Finished submission!");
        } else {
            System.out.println("user" + getClientId() + ": Retrying submit report!");
            submitLocationReportAtomic(ep);
        }
    }

    @Override
    void parseCommand(String cmd) throws JsonProcessingException {
        String[] args = cmd.split(" ");

        if (args.length < 2) {
            return;
        }

        try {
            switch (args[0]) {
                case LOCATION_PROOF_REQUEST: {
                    int ep = Integer.parseInt(args[1]);
                    int latitude = Integer.parseInt(args[2]);
                    int longitude = Integer.parseInt(args[3]);
                    createLocationReport(getClientId(), ep, latitude, longitude);
                    break;
                }
                case SUBMIT_LOCATION_REPORT: {
                    int ep = Integer.parseInt(args[1]);
                    submitLocationReportAtomic(ep);
                    break;
                }
                case OBTAIN_LOCATION_REPORT: {
                    int ep = Integer.parseInt(args[1]);
                    obtainLocationReportAtomic(this.getClientId(), ep);
                    break;
                }
                default:
                    System.out.println("Invalid operation or invalid number of arguments. Possible operations are proof, submit and obtain.");
                    break;
            }
        } catch (InterruptedException ex) {
            System.err.println("Caught Interrupted exception");
        } catch (StatusRuntimeException ex) {
            System.err.println(ex.getMessage());
        } catch (GeneralSecurityException ex) {
            System.err.println("Caught No Such Algorithm exception");
        }
    }

    public Location getMyLocation(int ep) {
        return this.getSystemInfo().getGrid().stream().filter(location ->
                location.getEp() == ep &&
                        location.getUserId() == this.getClientId()).collect(Collectors.toList()).get(0);
    }

    public boolean verifyLocationProofRequest(Proximity.LocationProofRequest request) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();
        LocationProof locationProof = objectMapper.readValue(request.getContent(), LocationProof.class);

        int userId = locationProof.getUserId();
        int epoch = locationProof.getEp();
        int latitude = locationProof.getLatitude();
        int longitude = locationProof.getLongitude();
        int witness = locationProof.getWitnessId();

        System.out.println("user" + getClientId() + ": Verifying location proof request from user" + userId);

        Location myLocation = getMyLocation(epoch);

        return this.getSystemInfo().getGrid().stream()
                .filter(location -> location.getEp() == epoch &&
                        location.getUserId() == userId &&
                        location.getLatitude() == latitude &&
                        location.getLongitude() == longitude &&
                        witness == this.getClientId())
                .count() == 1 &&
                isNearby(latitude, longitude, myLocation.getLatitude(), myLocation.getLongitude()) &&
                Crypto.verify(request.getContent(), request.getSignature(), this.getUserPublicKey(userId));
    }

    public Proximity.LocationProofResponse buildLocationProof(Proximity.LocationProofRequest request) throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        LocationProof locationProof = objectMapper.readValue(request.getContent(), LocationProof.class);

        String content = locationProof.toJsonString();

        return Proximity.LocationProofResponse
                .newBuilder()
                .setContent(content)
                .setSignature(Crypto.sign(content, this.getPrivateKey()))
                .build();
    }

    public void cleanup() {
        locationReports.clear();
        getSystemInfo().setAutomaticTransitions(false, Optional.empty());
    }
}
