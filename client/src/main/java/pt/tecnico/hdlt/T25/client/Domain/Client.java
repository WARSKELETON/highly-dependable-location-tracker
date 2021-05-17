package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.javatuples.Pair;
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
    static final String REQUEST_MY_PROOFS = "requestProofs";
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

            return areLocationProofsEqual(locationProof, originalLocationProof);
        } catch (JsonProcessingException ex) {
            System.err.println("Failed to process JSON in proof response.");
        }
        return false;
    }

    boolean areLocationProofsEqual(LocationProof locationProof1, LocationProof locationProof2) {
        return locationProof1.getEp() == locationProof2.getEp() &&
                locationProof1.getUserId() == locationProof2.getUserId() &&
                locationProof1.getLatitude() == locationProof2.getLatitude() &&
                locationProof1.getLongitude() == locationProof2.getLongitude() &&
                locationProof1.getWitnessId() == locationProof2.getWitnessId();
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

        SubmitLocationReportRequestHeader header = new SubmitLocationReportRequestHeader(this.getClientId(), serverId, "", 0);
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
        String requestContent = content + locationReport.getLocationProofsContent().values().stream().reduce("", String::concat) + secretKeySpec.toString() + header.getServerId() + header.getClientId();
        generateProofOfWork(header, requestContent);
        String requestSignatureContent = requestContent + header.getProofOfWork() + header.getCounter();

        return LocationServer.SubmitLocationReportRequest.newBuilder()
                .setKey(Crypto.encryptRSA(Base64.getEncoder().encodeToString(encodedKey), this.getServerPublicKey(serverId)))
                .setLocationProver(
                        LocationServer.LocationMessage.newBuilder()
                                .setContent(Crypto.encryptAES(secretKeySpec, content))
                                .setSignature(locationReport.getLocationProverSignature())
                                .build())
                .addAllLocationProofs(locationProofMessages)
                .setHeader(Crypto.encryptAES(secretKeySpec, header.toJsonString()))
                .setRequestSignature(Crypto.sign(requestSignatureContent, this.getPrivateKey()))
                .build();
    }

    public boolean submitLocationReportAtomic(int ep, int maxRequests) throws InterruptedException, GeneralSecurityException {
        final CountDownLatch finishLatch = new CountDownLatch((getMaxReplicas() + getMaxByzantineReplicas()) / 2 + 1);

        if (maxRequests == -1) {
            System.out.println("user" + getClientId() + ": Giving up trying to submit report!");
            return false;
        }

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

        for (int serverId : getLocationServerServiceStubs().keySet()) {
            LocationServer.SubmitLocationReportRequest request = buildSubmitLocationReportRequest(ep, serverId);
            submitLocationReport(getLocationServerServiceStubs().get(serverId), request, requestOnSuccessObserver, requestOnErrorObserver);
        }

        finishLatch.await(10, TimeUnit.SECONDS);

        if (finishLatch.getCount() == 0) {
            System.out.println("user" + getClientId() + ": Finished submission!");
            return true;
        } else {
            System.out.println("user" + getClientId() + ": Retrying submit report!");
            return submitLocationReportAtomic(ep, --maxRequests);
        }
    }

    private LocationServer.RequestMyProofsRequest buildRequestMyProofsRequest(int userId, Set<Integer> eps, int currentSeqNumber, int serverId) throws GeneralSecurityException {
        ProofsRequest proofsRequest = new ProofsRequest(userId, eps, currentSeqNumber, serverId);
        String requestContent = proofsRequest.toJsonString();

        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        String signatureString = requestContent + secretKeySpec.toString();

        return LocationServer.RequestMyProofsRequest.newBuilder()
                .setKey(Crypto.encryptRSA(Base64.getEncoder().encodeToString(encodedKey), this.getServerPublicKey(serverId)))
                .setContent(Crypto.encryptAES(secretKeySpec, requestContent))
                .setSignature(Crypto.sign(signatureString, this.getPrivateKey()))
                .build();
    }

    private Map<Pair<Integer, Integer>, LocationProof> verifyMyProofsResponse(int clientId, Set<Integer> eps, LocationServer.RequestMyProofsResponse response) throws GeneralSecurityException, JsonProcessingException {
        Map<Pair<Integer, Integer>, LocationProof> locationProofs = new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();

        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), this.getPrivateKey());
        if (secretKeySpec == null) return null;

        int receivedSeqNumber = Integer.parseInt(Crypto.decryptAES(secretKeySpec, response.getSeqNumber()));
        int serverId = Integer.parseInt(Crypto.decryptAES(secretKeySpec, response.getServerId()));
        int currentSeqNumber = this.getSeqNumbers().get(serverId);

        if (receivedSeqNumber != currentSeqNumber) {
            System.out.println("user" + getClientId() + ": Server" + serverId + " should not be trusted! Received sequence number " + receivedSeqNumber + " but expected " + currentSeqNumber);
            return null;
        }

        for (LocationServer.LocationMessage locationProof : response.getLocationProofsList()) {
            String locationProofContent = Crypto.decryptAES(secretKeySpec, locationProof.getContent());
            LocationProof proof = objectMapper.readValue(locationProofContent, LocationProof.class);

            if (proof.getWitnessId() != clientId || !eps.contains(proof.getEp()) || !Crypto.verify(locationProofContent, locationProof.getSignature(), this.getUserPublicKey(proof.getWitnessId()))) {
                System.out.println("user" + getClientId() + ": Server" + serverId + " should not be trusted! Returned illegitimate proof! User " + clientId + " did not prove user" + proof.getUserId() + " location at " + proof.getEp() + " " + proof.getLatitude() + ", " + proof.getLongitude());
                continue;
            }
            locationProofs.put(new Pair<>(proof.getUserId(), proof.getEp()), proof);
        }

        return locationProofs;
    }

    public List<LocationProof> requestMyProofsRegular(int userId, Set<Integer> eps) throws GeneralSecurityException, InterruptedException, JsonProcessingException {
        Map<Pair<Integer, Integer>, LocationProof> locationProofs = new HashMap<>();

        final CountDownLatch finishLatch = new CountDownLatch((getMaxReplicas() + getMaxByzantineReplicas()) / 2 + 1);

        Consumer<LocationServer.RequestMyProofsResponse> requestObserver = new Consumer<>() {
            @Override
            public void accept(LocationServer.RequestMyProofsResponse response)  {
                synchronized (finishLatch) {
                    if (finishLatch.getCount() == 0) return;

                    try {
                        Map<Pair<Integer, Integer>, LocationProof> proofs = verifyMyProofsResponse(userId, eps, response);
                        if (proofs != null) {
                            locationProofs.putAll(proofs);
                            finishLatch.countDown();
                        }
                    } catch (JsonProcessingException | GeneralSecurityException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        for (int serverId : getLocationServerServiceStubs().keySet()) {
            LocationServer.RequestMyProofsRequest request = buildRequestMyProofsRequest(userId, eps, this.getSeqNumbers().get(serverId), serverId);
            requestMyProofs(getLocationServerServiceStubs().get(serverId), request, requestObserver);
            getSeqNumbers().put(serverId, getSeqNumbers().get(serverId) + 1);
        }

        finishLatch.await(10, TimeUnit.SECONDS);

        if (finishLatch.getCount() == 0) {

            if (locationProofs.isEmpty()) {
                System.out.println("user" + getClientId() + ": User " + userId + " has not proved any location yet.");
                return new ArrayList<>();
            }

            for (LocationProof proof : locationProofs.values()) {
                System.out.println("user" + getClientId() + ": User " + proof.getWitnessId() + " proved user" + proof.getUserId() + " location at " + proof.getEp() + " " + proof.getLatitude() + ", " + proof.getLongitude());
            }

            return new ArrayList<>(locationProofs.values());
        } else {
            return requestMyProofsRegular(userId, eps);
        }
    }

    private void requestMyProofs(LocationServerServiceGrpc.LocationServerServiceStub locationServerServiceStub, LocationServer.RequestMyProofsRequest request, Consumer<LocationServer.RequestMyProofsResponse> callback) throws GeneralSecurityException {
        try {
            locationServerServiceStub.withDeadlineAfter(1, TimeUnit.SECONDS).requestMyProofs(request, new StreamObserver<>() {
                @Override
                public void onNext(LocationServer.RequestMyProofsResponse response) {
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
    void parseCommand(String cmd) throws JsonProcessingException {
        String[] args = cmd.split(" ");

        if (args.length < 2) {
            System.out.println("Invalid number of arguments.");
            return;
        }

        try {
            switch (args[0]) {
                case LOCATION_PROOF_REQUEST: {
                    if (args.length < 4) {
                        System.out.println("Invalid number of arguments. Try, proof ${ep} ${latitude} ${longitude}");
                        break;
                    }
                    int ep = Integer.parseInt(args[1]);
                    int latitude = Integer.parseInt(args[2]);
                    int longitude = Integer.parseInt(args[3]);
                    createLocationReport(getClientId(), ep, latitude, longitude);
                    break;
                }
                case SUBMIT_LOCATION_REPORT: {
                    int ep = Integer.parseInt(args[1]);
                    submitLocationReportAtomic(ep, 10);
                    break;
                }
                case OBTAIN_LOCATION_REPORT: {
                    int ep = Integer.parseInt(args[1]);
                    obtainLocationReportAtomic(this.getClientId(), ep);
                    break;
                }
                case REQUEST_MY_PROOFS: {
                    int i;
                    Set<Integer> eps = new HashSet<>();

                    for (i = 1; i < args.length; i++) {
                        eps.add(Integer.parseInt(args[i]));
                    }

                    requestMyProofsRegular(getClientId(), eps);
                    break;
                }
                default:
                    System.out.println("Invalid operation or invalid number of arguments. Possible operations are proof, submit, obtain and requestProofs.");
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
        this.cleanSeqNumbers();
        getSystemInfo().setAutomaticTransitions(false, Optional.empty());
    }
}
