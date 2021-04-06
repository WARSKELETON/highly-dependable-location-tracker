package pt.tecnico.hdlt.T25.server.Domain;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import org.javatuples.Pair;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.crypto.Crypto;
import pt.tecnico.hdlt.T25.server.Services.LocationServerServiceImpl;

import java.io.File;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static pt.tecnico.hdlt.T25.server.ServerApp.SERVER_RECOVERY_FILE_PATH;

public class Server {
    private int port;
    private int numberOfUsers;
    private int step;
    private int maxNearbyByzantineUsers;

    private final PrivateKey privateKey;
    private Map<Integer, PublicKey> clientPublicKeys;
    private Map<Pair<Integer, Integer>, LocationReport> locationReports; // <UserId, Epoch> to Location Report

    public Server(int port, int numberOfUsers, int step, int maxNearbyByzantineUsers) throws IOException, InterruptedException {
        this.port = port;
        this.numberOfUsers = numberOfUsers;
        this.step = step;
        this.maxNearbyByzantineUsers = maxNearbyByzantineUsers;
        this.locationReports = new HashMap<>();
        this.clientPublicKeys = new HashMap<>();
        this.privateKey = Crypto.getPriv("server-priv.key");
        this.loadPublicKeys();
        this.loadPreviousState();
        this.startServer();
    }

    private void loadPreviousState() {
        try {
            JsonFactory jsonFactory = new JsonFactory();
            ObjectMapper objectMapper = new ObjectMapper(jsonFactory);

            JsonNode objectNode = objectMapper.readTree(new File(SERVER_RECOVERY_FILE_PATH));
            objectMapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);

            port = objectNode.get("port").asInt();
            numberOfUsers = objectNode.get("numberOfUsers").asInt();
            step = objectNode.get("step").asInt();
            maxNearbyByzantineUsers = objectNode.get("maxNearbyByzantineUsers").asInt();
            JsonNode arrayNode = objectNode.get("locationReports");

            for (JsonNode jsonNode : arrayNode) {
                Integer userId = jsonNode.get("userId").asInt();
                Integer epoch = jsonNode.get("epoch").asInt();
                String res = jsonNode.get("content").asText();

                LocationReport locationReport = objectMapper.readValue(res, LocationReport.class);

                locationReports.put(new Pair<>(userId, epoch), locationReport);
            }
        }
        catch (IOException e) {
            System.out.println("Failed to parse previous state.");
            System.out.println(e.getMessage());
        }
    }
    private void startServer() throws IOException, InterruptedException {
        final BindableService impl = new LocationServerServiceImpl(this);

        io.grpc.Server server = ServerBuilder.forPort(port).addService(impl).build();

        server.start();

        System.out.println("Server started");
        server.awaitTermination();
    }

    private PublicKey getUserPublicKey(int userId) {
        return clientPublicKeys.get(userId);
    }

    private void loadPublicKeys() {
        for (int i = 0; i < this.numberOfUsers; i++) {
            String fileName = "client" + i + "-pub.key";
            this.clientPublicKeys.put(i, Crypto.getPub(fileName));
        }
        this.clientPublicKeys.put(-1, Crypto.getPub("ha-pub.key"));
    }

    public boolean verifyLocationReport(LocationServer.SubmitLocationReportRequest report) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        List<Integer> witnessIds = new ArrayList<>();

        String locationProverContent = Crypto.decryptRSA(report.getLocationProver().getContent(), this.privateKey);
        if (locationProverContent == null) return false;

        Location locationProver = objectMapper.readValue(locationProverContent, Location.class);
        // Duplicate location reports are deemed illegitimate
        boolean locationReportExists = locationReports.get(new Pair<>(locationProver.getUserId(), locationProver.getEp())) != null;
        if (locationReportExists) return false;

        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();

        System.out.println("User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());

        // Check each location proof in the report
        for (LocationServer.LocationMessage locationProof : report.getLocationProofsList()) {
            String locationProofContent = Crypto.decryptRSA(locationProof.getContent(), this.privateKey);
            if (locationProofContent == null) continue;

            LocationProof proof = objectMapper.readValue(locationProofContent, LocationProof.class);
            int witnessId = proof.getWitnessId();

            locationProofsContent.put(witnessId, locationProofContent);
            locationProofsSignatures.put(witnessId, locationProof.getSignature());

            // Witness must be distinct, different from Prover, the location proof matches prover's location and the signature is correct & authentic (from the witness)
            if (!witnessIds.contains(witnessId) && witnessId != locationProver.getUserId() && this.verifyLocationProof(proof, locationProver) && Crypto.verify(locationProofContent, locationProof.getSignature(), this.getUserPublicKey(witnessId))) {
                witnessIds.add(witnessId);
                System.out.println("Witness" + witnessId + " witnessed User" + proof.getUserId() + " at " + proof.getEp() + " " + proof.getLatitude() +  ", " + proof.getLongitude());
            } else {
                System.out.println("Obtained illegitimate witness proof from Witness" + proof.getWitnessId() + " where User" + proof.getUserId() + " would be at " + proof.getEp() + " " + proof.getLatitude() +  ", " + proof.getLongitude());
            }
        }

        // Verify the whole report content
        if (!Crypto.verify(locationProverContent + locationProofsContent.values().stream().reduce("", String::concat), report.getLocationProver().getSignature(), this.getUserPublicKey(locationProver.getUserId()))) {
            System.out.println("Report failed integrity or authentication checks! User" + locationProver.getUserId() + " would be at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
            return false;
        }

        if (witnessIds.size() >= maxNearbyByzantineUsers + 1) {
            LocationReport locationReport = new LocationReport(locationProver, report.getLocationProver().getSignature(), locationProofsContent, locationProofsSignatures);
            locationReports.put(new Pair<>(locationProver.getUserId(), locationProver.getEp()), locationReport);
            this.saveCurrentServerState();
            return true;
        }
        return false;
    }

    private boolean verifyLocationProof(LocationProof proof, Location locationProver) {
        return proof.getEp() == locationProver.getEp() &&
                proof.getUserId() == locationProver.getUserId() &&
                proof.getLatitude() == locationProver.getLatitude() &&
                proof.getLongitude() == locationProver.getLongitude();
    }

    public LocationServer.ObtainLocationReportResponse obtainLocationReport(LocationServer.ObtainLocationReportRequest report) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String requestContent = Crypto.decryptRSA(report.getContent(), this.privateKey);
        LocationReportRequest locationRequest = objectMapper.readValue(requestContent, LocationReportRequest.class);

        if (!Crypto.verify(requestContent, report.getSignature(), this.getUserPublicKey(locationRequest.getSourceClientId()))) {
            System.out.println("Some user tried to illegitimately request " + locationRequest.getUserId() + " location at epoch " + locationRequest.getEp());
            return LocationServer.ObtainLocationReportResponse.newBuilder().build();
        }

        LocationReport locationReport = locationReports.get(new Pair<>(locationRequest.getUserId(), locationRequest.getEp()));
        if (locationReport == null) {
            return LocationServer.ObtainLocationReportResponse.newBuilder().build();
        }

        System.out.println("Obtaining location for " + locationRequest.getUserId() + " at epoch " + locationRequest.getEp());

        return this.getLocationReportResponse(locationReport, locationRequest.getSourceClientId());
    }

    private LocationServer.ObtainLocationReportResponse getLocationReportResponse(LocationReport report, int userId) {
        List<LocationServer.LocationMessage> locationProofMessages = new ArrayList<>();

        for (Integer witnessId : report.getLocationProofsContent().keySet()) {
            String locationProofContent = report.getLocationProofsContent().get(witnessId);

            locationProofMessages.add(
                    LocationServer.LocationMessage.newBuilder()
                            .setContent(Crypto.encryptRSA(locationProofContent, this.getUserPublicKey(userId)))
                            .setSignature(report.getLocationProofsSignature().get(witnessId))
                            .build()
            );
        }

        String serverSignature = report.getLocationProver().toJsonString() + report.getLocationProofsContent().values().stream().reduce("", String::concat);
        return LocationServer.ObtainLocationReportResponse.newBuilder()
                .setLocationProver(
                        LocationServer.LocationMessage.newBuilder()
                                .setContent(Crypto.encryptRSA(report.getLocationProver().toJsonString(), this.getUserPublicKey(userId)))
                                .setSignature(report.getLocationProverSignature())
                                .build())
                .addAllLocationProofs(locationProofMessages)
                .setServerSignature(Crypto.sign(serverSignature, this.privateKey))
                .build();
    }

    public LocationServer.ObtainUsersAtLocationResponse obtainUsersAtLocation(LocationServer.ObtainUsersAtLocationRequest request) throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        String requestContent = Crypto.decryptRSA(request.getContent(), this.privateKey);
        Location locationRequest = objectMapper.readValue(requestContent, Location.class);

        if (locationRequest == null) {
            return LocationServer.ObtainUsersAtLocationResponse.newBuilder().build();
        }

        if (!Crypto.verify(requestContent, request.getSignature(), this.getUserPublicKey(-1))) {
            System.out.println("Some user tried to illegitimately request " + locationRequest.getUserId() + " location at epoch " + locationRequest.getEp());
            return LocationServer.ObtainUsersAtLocationResponse.newBuilder().build();
        }

        List<LocationServer.ObtainLocationReportResponse> locationReportResponses = new ArrayList<>();
        for (int i = 0; i < this.numberOfUsers; i++) {
            LocationReport report = locationReports.get(new Pair<>(i, locationRequest.getEp()));
            if (report != null && report.getLocationProver().getLongitude() == locationRequest.getLongitude() && report.getLocationProver().getLatitude() == locationRequest.getLatitude()) {
                locationReportResponses.add(this.getLocationReportResponse(report, -1));
            }
        }
        System.out.println("Obtaining location for " + locationRequest.getUserId() + " at epoch " + locationRequest.getEp());

        return LocationServer.ObtainUsersAtLocationResponse.newBuilder()
                .addAllLocationReports(locationReportResponses)
                .build();
    }

    private void saveCurrentServerState() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ArrayNode arrayNode = objectMapper.createArrayNode();

            for (Pair<Integer, Integer> key: locationReports.keySet()) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("userId", key.getValue0());
                node.put("epoch", key.getValue1());
                node.put("content", locationReports.get(key).toJsonString());

                arrayNode.add(node);
            }

            ObjectNode node = objectMapper.createObjectNode();
            node.put("port", port);
            node.put("numberOfUsers", numberOfUsers);
            node.put("step", step);
            node.put("maxNearbyByzantineUsers", maxNearbyByzantineUsers);
            node.set("locationReports", arrayNode);

            objectMapper.writeValue(new File(SERVER_RECOVERY_FILE_PATH), node);
        } catch (IOException e) {
            System.out.println("Failed to save current server state.");
        }
    }
}
