package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import pt.tecnico.hdlt.T25.MessageServiceGrpc;
import pt.tecnico.hdlt.T25.Proximity;
import pt.tecnico.hdlt.T25.ProximityServiceGrpc;
import pt.tecnico.hdlt.T25.client.Sevices.ProximityServiceImpl;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Client {
    private static final String LOCATION_PROOF_REQUEST = "proof";
    private static final int CLIENT_ORIGINAL_PORT = 8000;

    private String serverHost;
    private int serverPort;
    private int clientId;
    private final SystemInfo systemInfo;
    private MessageServiceGrpc.MessageServiceBlockingStub messageServiceStub;
    private Map<Integer, ProximityServiceGrpc.ProximityServiceBlockingStub> proximityServiceStubs;

    public Client(String serverHost, int serverPort, int clientId, SystemInfo systemInfo) throws IOException {
        this.clientId = clientId;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.systemInfo = systemInfo;
        this.proximityServiceStubs = new HashMap<>();
        this.connectToClients();
        this.connectToServer();
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
        messageServiceStub = MessageServiceGrpc.newBlockingStub(channel);
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

    private void requestLocationProof(int ep) {
        Location location = systemInfo.getGrid().stream()
                .filter(location1 -> location1.getEp() == ep && location1.getUserId() == clientId)
                .collect(Collectors.toList())
                .get(0);

        List<Integer> nearbyUsers = getNearbyUsers(location);

        System.out.println(nearbyUsers.size());
        for (int witnessId : nearbyUsers) {
            System.out.println(String.format("Sending Location Proof Request to %s...", witnessId));

            LocationProof locationProof = new LocationProof(location.getUserId(), location.getEp(), location.getLatitude(), location.getLongitude(), witnessId);
            Proximity.LocationProofRequest request = Proximity.LocationProofRequest.newBuilder()
                    .setContent(locationProof.toJsonString())
                    .build();

            Proximity.LocationProofResponse response = proximityServiceStubs.get(witnessId).requestLocationProof(request);
            System.out.println(String.format("Received Proof from %s...", witnessId));
        }
    }

    private void parseCommand(String cmd) {
        String[] args = cmd.split(" ");

        if (args.length != 2) {
            return;
        }

        if (args[0].equals(LOCATION_PROOF_REQUEST)) {
            int ep = Integer.parseInt(args[1]);
            requestLocationProof(ep);
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

        return systemInfo.getGrid().stream().filter(location ->
                location.getEp() == epoch &&
                        location.getUserId() == userId &&
                        location.getLatitude() == latitude &&
                        location.getLongitude() == longitude &&
                        witness == clientId)
                .collect(Collectors.toList()).size() == 1 &&
                isNearby(latitude, longitude, myLocation.getLatitude(), myLocation.getLongitude());
    }
}
