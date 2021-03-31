package pt.tecnico.hdlt.T25.server.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import org.javatuples.Pair;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.crypto.Crypto;
import pt.tecnico.hdlt.T25.server.Services.LocationServerServiceImpl;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
    private int port;
    private int numberOfUsers;
    private int step;

    private final PrivateKey privateKey;
    private Map<Integer, PublicKey> clientPublicKeys;
    private Map<Pair<Integer, Integer>, LocationReport> locationReports; // <UserId, Epoch> to Location Report

    public Server(int port, int numberOfUsers, int step) throws IOException, InterruptedException {
        this.port = port;
        this.numberOfUsers = numberOfUsers;
        this.step = step;
        this.locationReports = new HashMap<>();
        this.clientPublicKeys = new HashMap<>();
        this.privateKey = Crypto.getPriv("server-priv.key");
        this.loadPublicKeys();
        this.startServer();
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
        String locationProofContent;
        int numberLegitProofs = 0;

        String locationProverContent = Crypto.decryptRSA(report.getLocationProver().getContent(), this.privateKey);
        if (locationProverContent == null) return false;

        Location locationProver = objectMapper.readValue(locationProverContent, Location.class);
        boolean locationReportExists = locationReports.get(new Pair<>(locationProver.getUserId(), locationProver.getEp())) != null;
        if (!Crypto.verify(locationProverContent, report.getLocationProver().getSignature(), this.getUserPublicKey(locationProver.getUserId())) || locationReportExists) {
            return false;
        }

        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();

        System.out.println("User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());

        for (LocationServer.LocationMessage locationProof : report.getLocationProofsList()) {
            locationProofContent = Crypto.decryptRSA(locationProof.getContent(), this.privateKey);
            if (locationProofContent == null) continue;

            LocationProof proof = objectMapper.readValue(locationProofContent, LocationProof.class);

            if (this.verifyLocationProof(proof, locationProver) && Crypto.verify(locationProofContent, locationProof.getSignature(), this.getUserPublicKey(proof.getWitnessId()))) {
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
            locationReports.put(new Pair<>(locationProver.getUserId(), locationProver.getEp()), locationReport);
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
}
