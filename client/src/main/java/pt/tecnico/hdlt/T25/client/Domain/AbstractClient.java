package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.LocationServerServiceGrpc;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import javax.crypto.spec.SecretKeySpec;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class AbstractClient {
    private String serverHost;
    private int serverPort;
    private int clientId;
    private PrivateKey privateKey;
    private PublicKey serverPublicKey;
    private Map<Integer, PublicKey> publicKeys;
    private SystemInfo systemInfo;
    private LocationServerServiceGrpc.LocationServerServiceBlockingStub locationServerServiceStub;

    AbstractClient(String serverHost, int serverPort, int clientId, SystemInfo systemInfo) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.clientId = clientId;
        this.systemInfo = systemInfo;
        this.publicKeys = new HashMap<>();
        this.serverPublicKey = Crypto.getPub("server-pub.key");
        this.connectToServer();
        this.loadPublicKeys();
        this.updateEpochs();
    }

    String getServerHost() {
        return serverHost;
    }

    void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    int getServerPort() {
        return serverPort;
    }

    void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    int getClientId() {
        return clientId;
    }

    void setClientId(int clientId) {
        this.clientId = clientId;
    }

    PrivateKey getPrivateKey() {
        return privateKey;
    }

    void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    PublicKey getServerPublicKey() {
        return serverPublicKey;
    }

    void setServerPublicKey(PublicKey serverPublicKey) {
        this.serverPublicKey = serverPublicKey;
    }

    Map<Integer, PublicKey> getPublicKeys() {
        return publicKeys;
    }

    void setPublicKeys(Map<Integer, PublicKey> publicKeys) {
        this.publicKeys = publicKeys;
    }

    SystemInfo getSystemInfo() {
        return systemInfo;
    }

    void setSystemInfo(SystemInfo systemInfo) {
        this.systemInfo = systemInfo;
    }

    LocationServerServiceGrpc.LocationServerServiceBlockingStub getLocationServerServiceStub() {
        return locationServerServiceStub;
    }

    void setLocationServerServiceStub(LocationServerServiceGrpc.LocationServerServiceBlockingStub locationServerServiceStub) {
        this.locationServerServiceStub = locationServerServiceStub;
    }

    private void connectToServer() {
        String target = serverHost + ":" + serverPort;
        final ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        locationServerServiceStub = LocationServerServiceGrpc.newBlockingStub(channel);
    }

    boolean isNearby(double latitude1, double longitude1, double latitude2, double longitude2) {
        return Math.sqrt(Math.pow(latitude1 - latitude2, 2) + Math.pow(longitude1 - longitude2, 2)) <= systemInfo.getStep() + Math.round(systemInfo.getStep() / 2.0);
    }

    boolean verifyLocationProof(LocationProof proof, Location locationProver) {
        return proof.getEp() == locationProver.getEp() &&
                proof.getUserId() == locationProver.getUserId() &&
                proof.getLatitude() == locationProver.getLatitude() &&
                proof.getLongitude() == locationProver.getLongitude();
    }

    boolean verifyLocationReport(LocationServer.ObtainLocationReportResponse response) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), this.privateKey);
        if (secretKeySpec == null || response.getLocationProver() == null) return false;

        String locationProverContent = Crypto.decryptAES(secretKeySpec, response.getLocationProver().getContent());
        Location locationProver = objectMapper.readValue(locationProverContent, Location.class);
        List<String> locationProofsContent = new ArrayList<>();

        for (LocationServer.LocationMessage locationProof : response.getLocationProofsList()) {
            String locationProofContent = Crypto.decryptAES(secretKeySpec, locationProof.getContent());
            LocationProof proof = objectMapper.readValue(locationProofContent, LocationProof.class);
            locationProofsContent.add(locationProofContent);

            // A single illegitimate proof found in the report should invalidate the whole report
            if (!(this.verifyLocationProof(proof, locationProver) && Crypto.verify(locationProofContent, locationProof.getSignature(), this.getUserPublicKey(proof.getWitnessId())))) {
                System.out.println("2");
                System.out.println("Server should not be trusted! Generated illegitimate report for " + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
                return false;
            }
        }

        // Verify inner report client signature and outer server signature
        String reportContentString = locationProverContent + locationProofsContent.stream().reduce("", String::concat);
        if (!Crypto.verify(reportContentString, response.getLocationProver().getSignature(), this.getUserPublicKey(locationProver.getUserId())) || !Crypto.verify(reportContentString, response.getServerSignature(), this.getServerPublicKey())) {
            System.out.println("Server should not be trusted! Generated illegitimate report for " + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
            return false;
        }

        System.out.println("Legitimate report! User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
        return true;
    }

    void obtainLocationReport(int userId, int ep) throws JsonProcessingException {
        LocationReportRequest locationRequest = new LocationReportRequest(userId, ep, 0, 0, this.clientId);
        String requestContent = locationRequest.toJsonString();



        LocationServer.ObtainLocationReportRequest request = LocationServer.ObtainLocationReportRequest.newBuilder()

                .setContent(Crypto.encryptRSA(requestContent, this.serverPublicKey))
                .setSignature(Crypto.sign(requestContent, this.privateKey))
                .build();

        LocationServer.ObtainLocationReportResponse response = locationServerServiceStub.obtainLocationReport(request);
        verifyLocationReport(response);
    }

    private void loadPublicKeys() {
        for (int i = 0; i < systemInfo.getNumberOfUsers(); i++) {
            String fileName = "client" + i + "-pub.key";
            this.publicKeys.put(i, Crypto.getPub(fileName));
        }
    }

    PublicKey getUserPublicKey(int userId) {
        return publicKeys.get(userId);
    }

    abstract void parseCommand(String cmd);

    void eventLoop() {
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

    void updateEpochs() {
        Runnable task = () -> {
            getSystemInfo().updateCurrentEp();
            System.out.println("Changed epoch: " + getSystemInfo().getCurrentEp());
        };

        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(task, getSystemInfo().getDurationEp(), getSystemInfo().getDurationEp(), TimeUnit.SECONDS);
    }
}
