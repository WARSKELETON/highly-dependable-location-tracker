package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.LocationServerServiceGrpc;
import pt.tecnico.hdlt.T25.Proximity;
import pt.tecnico.hdlt.T25.ProximityServiceGrpc;
import pt.tecnico.hdlt.T25.client.Sevices.ProximityServiceImpl;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Client {
    private static final String LOCATION_PROOF_REQUEST = "proof";
    private static final String SUBMIT_LOCATION_REPORT = "submit";
    private static final String OBTAIN_LOCATION_REPORT = "obtain";
    private static final int CLIENT_ORIGINAL_PORT = 8000;

    private String serverHost;
    private int serverPort;
    private int clientId;
    private final PrivateKey privateKey;
    private final PublicKey serverPublicKey;
    private Map<Integer, PublicKey> publicKeys;
    private final SystemInfo systemInfo;
    private LocationServerServiceGrpc.LocationServerServiceBlockingStub locationServerServiceStub;
    private Map<Integer, ProximityServiceGrpc.ProximityServiceBlockingStub> proximityServiceStubs;
    private Map<Integer, LocationReport> locationReports;

    public Client(String serverHost, int serverPort, int clientId, SystemInfo systemInfo) throws IOException {
        this.clientId = clientId;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.systemInfo = systemInfo;
        this.locationReports = new HashMap<>();
        this.proximityServiceStubs = new HashMap<>();
        this.connectToClients();
        this.connectToServer();
        this.publicKeys = new HashMap<>();
        this.privateKey = Crypto.getPriv("client" + clientId + "-priv.key");
        this.serverPublicKey = Crypto.getPub("server-pub.key");
        this.loadPublicKeys();
        this.eventLoop();
    }

    private void connectToClients() throws IOException {
        int numberOfUsers = systemInfo.getNumberOfUsers();

        // Open "Server" for other Clients
        final BindableService proximityService = new ProximityServiceImpl(this);
        io.grpc.Server proximityServer = ServerBuilder.forPort(CLIENT_ORIGINAL_PORT + clientId).addService(proximityService).build();
        proximityServer.start();

        // Connect to all other Clients
        for (int i = 0; i < numberOfUsers; i++) {
            if (i == clientId) continue;

            int port = CLIENT_ORIGINAL_PORT + i;
            String target = "localhost:" + port;
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            ProximityServiceGrpc.ProximityServiceBlockingStub stub = ProximityServiceGrpc.newBlockingStub(channel);
            proximityServiceStubs.put(i, stub);
        }
    }

    private void connectToServer() {
        String target = serverHost + ":" + serverPort;
        final ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        locationServerServiceStub = LocationServerServiceGrpc.newBlockingStub(channel);
    }

    private boolean isNearby(double latitude1, double longitude1, double latitude2, double longitude2) {
        return Math.sqrt(Math.pow(latitude1 - latitude2, 2) + Math.pow(longitude1 - longitude2, 2)) <= systemInfo.getStep() + Math.round(systemInfo.getStep() / 2.0);
    }

    private List<Integer> getNearbyUsers(Location location) {
        int ep = location.getEp();
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        return systemInfo.getGrid().stream()
                .filter(location1 -> location1.getEp() == ep && location1.getUserId() != clientId && isNearby(latitude, longitude, location1.getLatitude(), location1.getLongitude()))
                .map(Location::getUserId)
                .collect(Collectors.toList());
    }

    private void requestLocationProof(int ep) throws JsonProcessingException {
        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();
        Location location = systemInfo.getGrid().stream()
                .filter(location1 -> location1.getEp() == ep && location1.getUserId() == clientId)
                .collect(Collectors.toList())
                .get(0);

        List<Integer> nearbyUsers = getNearbyUsers(location);
        System.out.println(nearbyUsers.size());
        for (int witnessId : nearbyUsers) {
            System.out.println(String.format("Sending Location Proof Request to %s...", witnessId));

            LocationProof locationProof = new LocationProof(location.getUserId(), location.getEp(), location.getLatitude(), location.getLongitude(), witnessId, 0, 0);

            String content = locationProof.toJsonString();

            Proximity.LocationProofRequest request = Proximity.LocationProofRequest.newBuilder()
                    .setContent(content)
                    .setSignature(Crypto.sign(content, this.privateKey))
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
        LocationReport locationReport = new LocationReport(location, locationProofsContent, locationProofsSignatures);
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
                    .setContent(Crypto.encryptRSA(locationReport.getLocationProofsContent().get(witnessId), this.serverPublicKey))
                    .setSignature(locationReport.getLocationProofsSignature().get(witnessId))
                    .build()
            );
        }

        String content = locationReport.getLocationProver().toJsonString();

        LocationServer.SubmitLocationReportRequest request = LocationServer.SubmitLocationReportRequest.newBuilder()
                .setLocationProver(
                        LocationServer.LocationMessage.newBuilder()
                                .setContent(Crypto.encryptRSA(content, this.serverPublicKey))
                                .setSignature(Crypto.sign(content, this.privateKey))
                                .build())
                .addAllLocationProofs(locationProofMessages)
                .build();

        LocationServer.SubmitLocationReportResponse response = locationServerServiceStub.submitLocationReport(request);
    }

    private void obtainLocationReport(int ep) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Location locationRequest = new Location(clientId, ep, 0, 0);
        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();

        LocationServer.ObtainLocationReportRequest request = LocationServer.ObtainLocationReportRequest.newBuilder()
                .setContent(locationRequest.toJsonString())
                .setSignature(locationRequest.toJsonString())
                .build();

        LocationServer.ObtainLocationReportResponse response = locationServerServiceStub.obtainLocationReport(request);
        Location locationProver = objectMapper.readValue(response.getLocationProver().getContent(), Location.class);

        System.out.println("I am " + locationProver.getUserId() + " and I've been at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());

        for (LocationServer.LocationMessage locationProof : response.getLocationProofsList()) {
            LocationProof proof = objectMapper.readValue(locationProof.getContent(), LocationProof.class);
            locationProofsContent.put(proof.getWitnessId(), locationProof.getContent());
            locationProofsSignatures.put(proof.getWitnessId(), locationProof.getSignature());
            System.out.println("Witness" + proof.getWitnessId() + " witnessed User" + proof.getUserId() + " at " + proof.getEp() + " " + proof.getLatitude() +  ", " + proof.getLongitude());
        }
    }

    private void parseCommand(String cmd) {
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
                obtainLocationReport(ep);
            } catch (JsonProcessingException ex) {
                System.err.println("Caught JSON Processing exception");
            }
        }

        else
            System.out.println("Type invalid. Possible types are car and person.");
    }

    private void eventLoop() {
        String line;

        try (Scanner scanner = new Scanner(System.in)) {

            while (true) {
                line = scanner.nextLine();

                if (line.isEmpty()) {
                    continue;
                }

                this.parseCommand(line);
            }
        } catch(Exception e2) {
            Logger.getLogger(Client.class.getName()).log(Level.WARNING, e2.getMessage(), e2);
            Thread.currentThread().interrupt();
        } finally {
            System.out.println("> Closing");
        }
    }

    private void loadPublicKeys() {
        for (int i = 0; i < systemInfo.getNumberOfUsers(); i++) {
            if (i == clientId) continue;

            String fileName = "client" + i + "-pub.key";
            this.publicKeys.put(i, Crypto.getPub(fileName));
        }
    }

    private PublicKey getUserPublicKey(int userId) {
        return publicKeys.get(userId);
    }

    private Location getMyLocation(int ep) {
        return systemInfo.getGrid().stream().filter(location ->
                location.getEp() == ep &&
                        location.getUserId() == clientId).collect(Collectors.toList()).get(0);
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

        return systemInfo.getGrid().stream()
                .filter(location -> location.getEp() == epoch &&
                        location.getUserId() == userId &&
                        location.getLatitude() == latitude &&
                        location.getLongitude() == longitude &&
                        witness == clientId)
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
                .setSignature(Crypto.sign(content, this.privateKey))
                .build();
    }
}
