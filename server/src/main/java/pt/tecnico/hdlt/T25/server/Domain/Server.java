package pt.tecnico.hdlt.T25.server.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import org.javatuples.Pair;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.Proximity;
import pt.tecnico.hdlt.T25.server.Services.LocationServerServiceImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
    private int port;
    // <UserId, Epoch> to Location Report
    private Map<Pair<Integer, Integer>, LocationReport> locationReports;

    public Server(int port) throws IOException, InterruptedException {
        this.port = port;
        this.locationReports = new HashMap<>();
        final BindableService impl = new LocationServerServiceImpl(this);

        io.grpc.Server server = ServerBuilder.forPort(port).addService(impl).build();

        server.start();

        System.out.println("Server started");
        server.awaitTermination();
    }

    public void verifyLocationReport(LocationServer.SubmitLocationReportRequest report) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Location locationProver = objectMapper.readValue(report.getLocationProver().getContent(), Location.class);
        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();

        System.out.println("User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());

        for (LocationServer.LocationMessage locationProof : report.getLocationProofsList()) {
            LocationProof proof = objectMapper.readValue(locationProof.getContent(), LocationProof.class);
            locationProofsContent.put(proof.getWitnessId(), locationProof.getContent());
            locationProofsSignatures.put(proof.getWitnessId(), locationProof.getSignature());
            System.out.println("Witness" + proof.getWitnessId() + " witnessed User" + proof.getUserId() + " at " + proof.getEp() + " " + proof.getLatitude() +  ", " + proof.getLongitude());
        }

        LocationReport locationReport = new LocationReport(locationProver, locationProofsContent, locationProofsSignatures);
        locationReports.put(new Pair<>(locationProver.getUserId(), locationProver.getEp()), locationReport);
    }

    public LocationServer.ObtainLocationReportResponse obtainLocationReport(LocationServer.ObtainLocationReportRequest report) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        Location locationRequest = objectMapper.readValue(report.getContent(), Location.class);
        LocationReport locationReport = locationReports.get(new Pair<>(locationRequest.getUserId(), locationRequest.getEp()));
        List<LocationServer.LocationMessage> locationProofMessages = new ArrayList<>();
        System.out.println("Obtaining location for " + locationRequest.getUserId() + " at epoch " + locationRequest.getEp());

        for (Integer witnessId : locationReport.getLocationProofsContent().keySet()) {
            locationProofMessages.add(
                    LocationServer.LocationMessage.newBuilder()
                            .setContent(locationReport.getLocationProofsContent().get(witnessId))
                            .setSignature(locationReport.getLocationProofsSignature().get(witnessId))
                            .build()
            );
        }

        return LocationServer.ObtainLocationReportResponse.newBuilder()
                .setLocationProver(
                        LocationServer.LocationMessage.newBuilder()
                                .setContent(locationReport.getLocationProver().toJsonString())
                                .setSignature(locationReport.getLocationProver().toJsonString())
                                .build())
                .addAllLocationProofs(locationProofMessages)
                .build();
    }
}
