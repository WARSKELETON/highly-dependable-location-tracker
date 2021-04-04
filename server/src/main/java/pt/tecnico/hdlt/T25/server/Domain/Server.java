package pt.tecnico.hdlt.T25.server.Domain;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import org.javatuples.Pair;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.crypto.Crypto;
import pt.tecnico.hdlt.T25.server.Domain.types.ClientPublicKeysType;
import pt.tecnico.hdlt.T25.server.Domain.types.LocationReportsType;
import pt.tecnico.hdlt.T25.server.Domain.types.PrivateKeyType;
import pt.tecnico.hdlt.T25.server.ServerApp;
import pt.tecnico.hdlt.T25.server.Services.LocationServerServiceImpl;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server implements Serializable {
    @JsonProperty("port")
    private final int port;
    @JsonProperty("numberOfUsers")
    private final int numberOfUsers;
    @JsonProperty("step")
    private final int step;
    @JsonIgnore
    private final PrivateKeyType privateKey;
    @JsonIgnore
    private final ClientPublicKeysType clientPublicKeys;
    @JsonProperty("locationReports")
    private final LocationReportsType locationReports; // <UserId, Epoch> to Location Report

    public Server(int port, int numberOfUsers, int step) throws IOException, InterruptedException {
        this.port = port;
        this.numberOfUsers = numberOfUsers;
        this.step = step;
        this.locationReports = new LocationReportsType();
        this.clientPublicKeys = new ClientPublicKeysType(this.numberOfUsers);
        this.privateKey = new PrivateKeyType(Crypto.getPriv("server-priv.key"));
        this.startServer();
    }

    @JsonCreator
    public Server(ObjectNode node) throws IOException, InterruptedException {
        this.port = node.get("port").asInt();
        this.numberOfUsers = node.get("numberOfUsers").asInt();
        this.step = node.get("step").asInt();
        this.locationReports = new ObjectMapper().readValue(node.get("locationReports").asText(), LocationReportsType.class);
        this.clientPublicKeys = new ClientPublicKeysType(this.numberOfUsers);
        this.privateKey = new PrivateKeyType(Crypto.getPriv("server-priv.key"));
        this.startServer();
    }

    private void startServer() throws IOException, InterruptedException {
        final BindableService impl = new LocationServerServiceImpl(this);

        io.grpc.Server server = ServerBuilder.forPort(port).addService(impl).build();

        server.start();

        System.out.println("Server started");
        server.awaitTermination();
    }

    public boolean verifyLocationReport(LocationServer.SubmitLocationReportRequest report) throws JsonProcessingException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        String locationProofContent;
        int numberLegitProofs = 0;

        String locationProverContent = Crypto.decryptRSA(report.getLocationProver().getContent(), this.privateKey.getPrivateKey());
        if (locationProverContent == null) return false;

        Location locationProver = objectMapper.readValue(locationProverContent, Location.class);
        boolean locationReportExists = this.locationReports.getLocationReport(locationProver.getUserId(), locationProver.getEp()) != null;
        if (!Crypto.verify(locationProverContent, report.getLocationProver().getSignature(), this.clientPublicKeys.getPublicKey(locationProver.getUserId())) || locationReportExists) {
            return false;
        }

        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();

        System.out.println("User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());

        for (LocationServer.LocationMessage locationProof : report.getLocationProofsList()) {
            locationProofContent = Crypto.decryptRSA(locationProof.getContent(), this.privateKey.getPrivateKey());
            if (locationProofContent == null) continue;

            LocationProof proof = objectMapper.readValue(locationProofContent, LocationProof.class);

            if (this.verifyLocationProof(proof, locationProver) && Crypto.verify(locationProofContent, locationProof.getSignature(), this.clientPublicKeys.getPublicKey(proof.getWitnessId()))) {
                numberLegitProofs++;

                locationProofsContent.put(proof.getWitnessId(), locationProofContent);
                locationProofsSignatures.put(proof.getWitnessId(), locationProof.getSignature());
                System.out.println("Witness" + proof.getWitnessId() + " witnessed User" + proof.getUserId() + " at " + proof.getEp() + " " + proof.getLatitude() +  ", " + proof.getLongitude());
            } else {
                System.out.println("Obtained illegitimate witness proof from Witness" + proof.getWitnessId() + " where User" + proof.getUserId() + " would be at " + proof.getEp() + " " + proof.getLatitude() +  ", " + proof.getLongitude());
            }
        }

        // TODO How many should be correct??
        if (report.getLocationProofsList().size() / 2 < numberLegitProofs) {
            LocationReport locationReport = new LocationReport(locationProver, report.getLocationProver().getSignature(), locationProofsContent, locationProofsSignatures);
            this.locationReports.updateLocationReports(locationProver.getUserId(), locationProver.getEp(), locationReport);
            saveCurrentServerState();
            return true;
        }
        return false;
    }

    private boolean verifyLocationProof(LocationProof proof, Location locationProver) {
        return proof.getEp() == locationProver.getEp() &&
                proof.getUserId() == locationProver.getUserId() &&
                proof.getLatitude() == locationProver.getLatitude() &&
                proof.getLongitude() == locationProver.getLongitude() &&
                isNearby(proof.getWitnessLatitude(), proof.getWitnessLongitude(), locationProver.getLatitude(), locationProver.getLongitude());
    }

    private boolean isNearby(double latitude1, double longitude1, double latitude2, double longitude2) {
        return Math.sqrt(Math.pow(latitude1 - latitude2, 2) + Math.pow(longitude1 - longitude2, 2)) <= this.step + Math.round(this.step / 2.0);
    }

    public LocationServer.ObtainLocationReportResponse obtainLocationReport(LocationServer.ObtainLocationReportRequest report) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String requestContent = Crypto.decryptRSA(report.getContent(), this.privateKey.getPrivateKey());
        LocationReportRequest locationRequest = objectMapper.readValue(requestContent, LocationReportRequest.class);

        if (!Crypto.verify(requestContent, report.getSignature(), this.clientPublicKeys.getPublicKey(locationRequest.getSourceClientId()))) {
            System.out.println("Some user tried to illegitimately request " + locationRequest.getUserId() + " location at epoch " + locationRequest.getEp());
            return LocationServer.ObtainLocationReportResponse.newBuilder().build();
        }

        LocationReport locationReport = this.locationReports.getLocationReport(locationRequest.getUserId(), locationRequest.getEp());
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
                            .setContent(Crypto.encryptRSA(locationProofContent, this.clientPublicKeys.getPublicKey(userId)))
                            .setSignature(report.getLocationProofsSignature().get(witnessId))
                            .build()
            );
        }

        return LocationServer.ObtainLocationReportResponse.newBuilder()
                .setLocationProver(
                        LocationServer.LocationMessage.newBuilder()
                                .setContent(report.getLocationProver().toJsonString())
                                .setSignature(report.getLocationProverSignature())
                                .build())
                .addAllLocationProofs(locationProofMessages)
                .build();
    }

    public LocationServer.ObtainUsersAtLocationResponse obtainUsersAtLocation(LocationServer.ObtainUsersAtLocationRequest request) throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        String requestContent = Crypto.decryptRSA(request.getContent(), this.privateKey.getPrivateKey());
        Location locationRequest = objectMapper.readValue(requestContent, Location.class);

        if (locationRequest == null) {
            return LocationServer.ObtainUsersAtLocationResponse.newBuilder().build();
        }

        if (!Crypto.verify(requestContent, request.getSignature(), this.clientPublicKeys.getPublicKey(-1))) {
            System.out.println("Some user tried to illegitimately request " + locationRequest.getUserId() + " location at epoch " + locationRequest.getEp());
            return LocationServer.ObtainUsersAtLocationResponse.newBuilder().build();
        }

        List<LocationServer.ObtainLocationReportResponse> locationReportResponses = new ArrayList<>();
        for (int i = 0; i < this.numberOfUsers; i++) {
            LocationReport report = this.locationReports.getLocationReport(i, locationRequest.getEp());
            if (report != null && report.getLocationProver().getLongitude() == locationRequest.getLongitude() && report.getLocationProver().getLatitude() == locationRequest.getLatitude()) {
                locationReportResponses.add(this.getLocationReportResponse(report, -1));
            }
        }
        System.out.println("Obtaining location for " + locationRequest.getUserId() + " at epoch " + locationRequest.getEp());

        return LocationServer.ObtainUsersAtLocationResponse.newBuilder()
                .addAllLocationReports(locationReportResponses)
                .build();
    }

    private void saveCurrentServerState() throws IOException {
        System.out.println("Saving current server state");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        ObjectNode node = objectMapper.createObjectNode();

        node.put("port", objectMapper.writeValueAsString(this.port));
        node.put("numberOfUsers", objectMapper.writeValueAsString(this.numberOfUsers));
        node.put("step", objectMapper.writeValueAsString(this.step));
        node.put("locationReports", objectMapper.writeValueAsString(this.locationReports));

        objectMapper.writeValue(new File(ServerApp.SERVER_RECOVERY_FILE_PATH), node);
    }
}

