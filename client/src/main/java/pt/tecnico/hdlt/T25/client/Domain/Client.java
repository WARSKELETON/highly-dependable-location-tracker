package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.Proximity;
import pt.tecnico.hdlt.T25.ProximityServiceGrpc;
import pt.tecnico.hdlt.T25.client.Services.ProximityServiceImpl;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static pt.tecnico.hdlt.T25.crypto.Crypto.getPriv;

public class Client extends AbstractClient {
    static final String LOCATION_PROOF_REQUEST = "proof";
    static final String SUBMIT_LOCATION_REPORT = "submit";
    static final String OBTAIN_LOCATION_REPORT = "obtain";
    private static final int CLIENT_ORIGINAL_PORT = 8000;

    private final int maxNearbyByzantineUsers;
    private Map<Integer, ProximityServiceGrpc.ProximityServiceStub> proximityServiceStubs;
    private Map<Integer, LocationReport> locationReports;

    public Client(String serverHost, int serverPort, int clientId, SystemInfo systemInfo, int maxNearbyByzantineUsers, boolean isTest) throws IOException, GeneralSecurityException {
        super(serverHost, serverPort, clientId, systemInfo);
        this.maxNearbyByzantineUsers = maxNearbyByzantineUsers;
        this.locationReports = new HashMap<>();
        this.proximityServiceStubs = new HashMap<>();
        this.connectToClients();
        this.setPrivateKey(getPriv("client" + clientId + "-priv.key"));
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

    boolean createLocationReport(int ep, int latitude, int longitude) throws InterruptedException {
        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();
        Location location = this.getSystemInfo().getGrid().stream()
                .filter(location1 -> location1.getEp() == ep && location1.getUserId() == this.getClientId())
                .collect(Collectors.toList())
                .get(0);

        List<Integer> nearbyUsers = getNearbyUsers(location);

        final CountDownLatch finishLatch = new CountDownLatch(this.maxNearbyByzantineUsers);

        System.out.println("Nearby users: " + nearbyUsers.size());
        for (int witnessId : nearbyUsers) {
            System.out.println(String.format("Sending Location Proof Request to %s...", witnessId));

            LocationProof locationProof = new LocationProof(location.getUserId(), location.getEp(), location.getLatitude(), location.getLongitude(), witnessId);

            Consumer<Proximity.LocationProofResponse> requestObserver = new Consumer<>() {
                @Override
                public void accept(Proximity.LocationProofResponse response) {
                    if (finishLatch.getCount() == 0) return;
                    if (Crypto.verify(response.getContent(), response.getSignature(), getUserPublicKey(witnessId)) && verifyLocationProofResponse(locationProof, response.getContent())) {
                        locationProofsContent.put(witnessId, response.getContent());
                        locationProofsSignatures.put(witnessId, response.getSignature());
                        System.out.println(String.format("Received legitimate proof from %s...", witnessId));
                        finishLatch.countDown();
                    } else {
                        System.out.println(String.format("Received illegitimate proof from %s...", witnessId));
                    }
                }
            };

            this.requestLocationProof(locationProof, witnessId, requestObserver);
        }

        finishLatch.await(1, TimeUnit.MINUTES);

        System.out.println("Count " + finishLatch.getCount());

        if (finishLatch.getCount() == 0) {
            String signature = location.toJsonString() + locationProofsContent.values().stream().reduce("", String::concat);
            LocationReport locationReport = new LocationReport(location, Crypto.sign(signature, this.getPrivateKey()), locationProofsContent, locationProofsSignatures);
            locationReports.put(ep, locationReport);
            return true;
        }
        return false;
    }

    void requestLocationProof(LocationProof locationProof, int witnessId, Consumer<Proximity.LocationProofResponse> callback) {

        String content = locationProof.toJsonString();

        Proximity.LocationProofRequest request = Proximity.LocationProofRequest.newBuilder()
                .setContent(content)
                .setSignature(Crypto.sign(content, this.getPrivateKey()))
                .build();

        proximityServiceStubs.get(witnessId).requestLocationProof(request, new StreamObserver<>() {
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

        System.out.println("Requested " + witnessId);
    }

    public void submitLocationReport(int ep) throws InterruptedException, GeneralSecurityException {
        LocationReport locationReport = locationReports.get(ep);
        Location myLocation = this.getMyLocation(ep);

        if (locationReport == null) {
            if (!createLocationReport(ep, myLocation.getLatitude(), myLocation.getLongitude())) return;
            locationReport = locationReports.get(ep);
        }

        List<LocationServer.LocationMessage> locationProofMessages = new ArrayList<>();

        for (Integer witnessId : locationReport.getLocationProofsContent().keySet()) {
            locationProofMessages.add(
                    LocationServer.LocationMessage.newBuilder()
                    .setContent(Crypto.encryptRSA(locationReport.getLocationProofsContent().get(witnessId), this.getServerPublicKey()))
                    .setSignature(locationReport.getLocationProofsSignature().get(witnessId))
                    .build()
            );
        }

        String content = locationReport.getLocationProver().toJsonString();

        LocationServer.SubmitLocationReportRequest request = LocationServer.SubmitLocationReportRequest.newBuilder()
                .setLocationProver(
                        LocationServer.LocationMessage.newBuilder()
                                .setContent(Crypto.encryptRSA(content, this.getServerPublicKey()))
                                .setSignature(locationReport.getLocationProverSignature())
                                .build())
                .addAllLocationProofs(locationProofMessages)
                .build();

        LocationServer.SubmitLocationReportResponse response = getLocationServerServiceStub().submitLocationReport(request);
    }

    @Override
    void parseCommand(String cmd) throws GeneralSecurityException, JsonProcessingException {
        String[] args = cmd.split(" ");

        if (args.length != 2) {
            return;
        }

        if (args[0].equals(LOCATION_PROOF_REQUEST)) {
            int ep = Integer.parseInt(args[1]);
            Location myLocation = getMyLocation(ep);
            try {
                createLocationReport(ep, myLocation.getLatitude(), myLocation.getLongitude());
            } catch (InterruptedException ex) {
                System.err.println("Caught Interrupted exception");
            }
        }

        else if (args[0].equals(SUBMIT_LOCATION_REPORT)) {
            int ep = Integer.parseInt(args[1]);
            try {
                submitLocationReport(ep);
            } catch (InterruptedException ex) {
                System.err.println("Caught Interrupted exception");
            } catch (StatusRuntimeException ex2) {
                System.err.println(ex2.getMessage());
            }
        }

        else if (args[0].equals(OBTAIN_LOCATION_REPORT)) {
            int ep = Integer.parseInt(args[1]);
            try {
                obtainLocationReport(this.getClientId(), ep);
            } catch (StatusRuntimeException ex2) {
                System.err.println(ex2.getMessage());
            }
        }

        else
            System.out.println("Type invalid. Possible types are car and person.");
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

        System.out.println(String.format("Verifying request from %d...", userId));

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
}
