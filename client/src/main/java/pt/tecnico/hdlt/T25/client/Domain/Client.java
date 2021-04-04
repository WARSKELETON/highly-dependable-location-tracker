package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.Proximity;
import pt.tecnico.hdlt.T25.ProximityServiceGrpc;
import pt.tecnico.hdlt.T25.client.Services.ProximityServiceImpl;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static pt.tecnico.hdlt.T25.crypto.Crypto.getPriv;

public class Client extends AbstractClient {
    private static final String LOCATION_PROOF_REQUEST = "proof";
    private static final String SUBMIT_LOCATION_REPORT = "submit";
    private static final String OBTAIN_LOCATION_REPORT = "obtain";
    private static final int CLIENT_ORIGINAL_PORT = 8000;

    private Map<Integer, ProximityServiceGrpc.ProximityServiceBlockingStub> proximityServiceStubs;
    private Map<Integer, LocationReport> locationReports;

    public Client(String serverHost, int serverPort, int clientId, SystemInfo systemInfo) throws IOException {
        super(serverHost, serverPort, clientId, systemInfo);
        this.locationReports = new HashMap<>();
        this.proximityServiceStubs = new HashMap<>();
        this.connectToClients();
        this.setPrivateKey(getPriv("client" + clientId + "-priv.key"));
        this.eventLoop();
    }

    public Map<Integer, ProximityServiceGrpc.ProximityServiceBlockingStub> getProximityServiceStubs() {
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
            ProximityServiceGrpc.ProximityServiceBlockingStub stub = ProximityServiceGrpc.newBlockingStub(channel);
            proximityServiceStubs.put(i, stub);
        }
    }

    List<Integer> getNearbyUsers(Location location) {
        int ep = location.getEp();
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        return this.getSystemInfo().getGrid().stream()
                .filter(location1 -> location1.getEp() == ep && location1.getUserId() != this.getClientId()&& isNearby(latitude, longitude, location1.getLatitude(), location1.getLongitude()))
                .map(Location::getUserId)
                .collect(Collectors.toList());
    }

    void requestLocationProof(int ep) throws JsonProcessingException {
        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();
        Location location = this.getSystemInfo().getGrid().stream()
                .filter(location1 -> location1.getEp() == ep && location1.getUserId() == this.getClientId())
                .collect(Collectors.toList())
                .get(0);

        List<Integer> nearbyUsers = getNearbyUsers(location);
        System.out.println("Nearby users: " + nearbyUsers.size());
        for (int witnessId : nearbyUsers) {
            System.out.println(String.format("Sending Location Proof Request to %s...", witnessId));

            LocationProof locationProof = new LocationProof(location.getUserId(), location.getEp(), location.getLatitude(), location.getLongitude(), witnessId, 0, 0);
            String content = locationProof.toJsonString();

            Proximity.LocationProofRequest request = Proximity.LocationProofRequest.newBuilder()
                    .setContent(content)
                    .setSignature(Crypto.sign(content, this.getPrivateKey()))
                    .build();

            Proximity.LocationProofResponse response = proximityServiceStubs.get(witnessId).requestLocationProof(request);

            if (Crypto.verify(response.getContent(), response.getSignature(), this.getUserPublicKey(witnessId)) && this.verifyLocationProofResponse(locationProof, response.getContent())) {
                locationProofsContent.put(witnessId, response.getContent());
                locationProofsSignatures.put(witnessId, response.getSignature());
                System.out.println(String.format("Received Proof from %s...", witnessId));
            } else {
                System.out.println(String.format("Illegitimate Proof from %s...", witnessId));
            }
        }

        String signature = location.toJsonString() + locationProofsSignatures.values().stream().reduce("", String::concat);
        LocationReport locationReport = new LocationReport(location, Crypto.sign(signature, this.getPrivateKey()), locationProofsContent, locationProofsSignatures);
        locationReports.put(ep, locationReport);
    }

    private boolean verifyLocationProofResponse(LocationProof originalLocationProof, String content) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        LocationProof locationProof = objectMapper.readValue(content, LocationProof.class);

        return locationProof.getEp() == originalLocationProof.getEp() &&
                locationProof.getUserId() == originalLocationProof.getUserId() &&
                locationProof.getLatitude() == originalLocationProof.getLatitude() &&
                locationProof.getLongitude() == originalLocationProof.getLongitude() &&
                locationProof.getWitnessId() == originalLocationProof.getWitnessId();
    }

    private void submitLocationReport(int ep) {
        LocationReport locationReport = locationReports.get(ep);
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
                                .setSignature(Crypto.sign(content, this.getPrivateKey()))
                                .build())
                .addAllLocationProofs(locationProofMessages)
                .build();

        LocationServer.SubmitLocationReportResponse response = getLocationServerServiceStub().submitLocationReport(request);
    }

    @Override
    void parseCommand(String cmd) {
        String[] args = cmd.split(" ");

        if (args.length != 2) {
            return;
        }

        if (args[0].equals(LOCATION_PROOF_REQUEST)) {
            int ep = Integer.parseInt(args[1]);
            try {
                requestLocationProof(ep);
            } catch (JsonProcessingException ex) {
                System.err.println("Caught JSON Processing exception");
            }
        }

        else if (args[0].equals(SUBMIT_LOCATION_REPORT)) {
            int ep = Integer.parseInt(args[1]);
            submitLocationReport(ep);
        }

        else if (args[0].equals(OBTAIN_LOCATION_REPORT)) {
            int ep = Integer.parseInt(args[1]);
            try {
                obtainLocationReport(this.getClientId(), ep);
            } catch (JsonProcessingException ex) {
                System.err.println("Caught JSON Processing exception");
            }
        }

        else
            System.out.println("Type invalid. Possible types are car and person.");
    }

    private Location getMyLocation(int ep) {
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

    public Proximity.LocationProofResponse requestLocationProof(Proximity.LocationProofRequest request) throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        LocationProof locationProof = objectMapper.readValue(request.getContent(), LocationProof.class);

        Location myLocation = getMyLocation(locationProof.getEp());

        locationProof.setWitnessLatitude(myLocation.getLatitude());
        locationProof.setWitnessLongitude(myLocation.getLongitude());

        String content = locationProof.toJsonString();

        return Proximity.LocationProofResponse
                .newBuilder()
                .setContent(content)
                .setSignature(Crypto.sign(content, this.getPrivateKey()))
                .build();
    }
}
