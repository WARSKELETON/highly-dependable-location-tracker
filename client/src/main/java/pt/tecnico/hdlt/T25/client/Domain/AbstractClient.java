package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.LocationServerServiceGrpc;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.grpc.Status.DEADLINE_EXCEEDED;

abstract class AbstractClient {
    private String serverHost;
    private int serverPort;
    private int clientId;
    private int seqNumber;
    private PrivateKey privateKey;
    private PublicKey serverPublicKey;
    private Map<Integer, PublicKey> publicKeys;
    private SystemInfo systemInfo;
    private final Object seqNumberLock = new Object();
    private LocationServerServiceGrpc.LocationServerServiceBlockingStub locationServerServiceStub;

    AbstractClient(String serverHost, int serverPort, int clientId, SystemInfo systemInfo) throws GeneralSecurityException, JsonProcessingException {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.clientId = clientId;
        this.systemInfo = systemInfo;
        this.seqNumber = 0;
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

    public int getClientId() {
        return clientId;
    }

    void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public PrivateKey getPrivateKey() {
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

    public int getSeqNumber() {
        return seqNumber;
    }

    public void setSeqNumber(int seqNumber) {
        this.seqNumber = seqNumber;
    }

    public Object getSeqNumberLock() {
        return seqNumberLock;
    }

    public LocationServerServiceGrpc.LocationServerServiceBlockingStub getLocationServerServiceStub() {
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

    boolean verifyLocationReport(LocationServer.ObtainLocationReportResponse response, int currentSeqNumber) throws JsonProcessingException, GeneralSecurityException {
        ObjectMapper objectMapper = new ObjectMapper();

        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), this.privateKey);
        if (secretKeySpec == null || response.getLocationProver() == null) return false;

        String locationProverContent = Crypto.decryptAES(secretKeySpec, response.getLocationProver().getContent());
        int receivedSeqNumber = Integer.parseInt(Crypto.decryptAES(secretKeySpec, response.getSeqNumber()));
        Location locationProver = objectMapper.readValue(locationProverContent, Location.class);
        List<String> locationProofsContent = new ArrayList<>();

        if (receivedSeqNumber != currentSeqNumber + 1) {
            System.out.println("user" + getClientId() + ": Server should not be trusted! Received sequence number " + receivedSeqNumber + " but expected " + currentSeqNumber + 1);
            return false;
        }

        for (LocationServer.LocationMessage locationProof : response.getLocationProofsList()) {
            String locationProofContent = Crypto.decryptAES(secretKeySpec, locationProof.getContent());
            LocationProof proof = objectMapper.readValue(locationProofContent, LocationProof.class);
            locationProofsContent.add(locationProofContent);

            // A single illegitimate proof found in the report should invalidate the whole report
            if (!(this.verifyLocationProof(proof, locationProver) && Crypto.verify(locationProofContent, locationProof.getSignature(), this.getUserPublicKey(proof.getWitnessId())))) {
                System.out.println("user" + getClientId() + ": Server should not be trusted! Generated illegitimate report for " + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
                return false;
            }
        }

        // Verify inner report client signature and outer server signature
        String reportContentString = locationProverContent + locationProofsContent.stream().reduce("", String::concat);
        String responseContentString = reportContentString + receivedSeqNumber;
        if (!Crypto.verify(reportContentString, response.getLocationProver().getSignature(), this.getUserPublicKey(locationProver.getUserId())) || !Crypto.verify(responseContentString, response.getServerSignature(), this.getServerPublicKey())) {
            System.out.println("user" + getClientId() + ": Server should not be trusted! Generated illegitimate report for " + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
            return false;
        }

        System.out.println("user" + getClientId() + ": Legitimate report! User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
        return true;
    }

    private void updateSeqNumber(LocationServer.ObtainLatestSeqNumberResponse response) throws GeneralSecurityException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), this.privateKey);
        String requestContent = Crypto.decryptAES(secretKeySpec, response.getContent());
        SeqNumberResponse seqNumberResponse = objectMapper.readValue(requestContent, SeqNumberResponse.class);
        int clientId = seqNumberResponse.getClientId();
        int seqNumber = seqNumberResponse.getSeqNumber();

        if (!Crypto.verify(requestContent, response.getSignature(), this.serverPublicKey) || clientId != getClientId()) {
            System.out.println("user" + getClientId() + ": invalid sequence number response");
            return;
        }

        this.seqNumber = seqNumber;
    }

    void checkLatestSeqNumber() throws GeneralSecurityException, JsonProcessingException {
        String requestContent = Integer.toString(clientId);

        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        LocationServer.ObtainLatestSeqNumberRequest request = LocationServer.ObtainLatestSeqNumberRequest.newBuilder()
                .setKey(Crypto.encryptRSA(Base64.getEncoder().encodeToString(encodedKey), this.serverPublicKey))
                .setContent(Crypto.encryptAES(secretKeySpec, requestContent))
                .setSignature(Crypto.sign(requestContent, this.privateKey))
                .build();

        LocationServer.ObtainLatestSeqNumberResponse response;
        try {
            response = locationServerServiceStub.withDeadlineAfter(1, TimeUnit.SECONDS).obtainLatestSeqNumber(request);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode().equals(DEADLINE_EXCEEDED.getCode())) {
                System.out.println("user" + getClientId() + ": TIMEOUT CLIENT");
                checkLatestSeqNumber();
                return;
            } else {
                throw e;
            }
        }

        if (response != null) {
            updateSeqNumber(response);
        }
    }

    public Location obtainLocationReport(int userId, int ep) throws JsonProcessingException, GeneralSecurityException {
        int currentSeqNumber;
        synchronized (seqNumberLock) {
            currentSeqNumber = this.seqNumber;
            this.seqNumber += 1;
        }

        LocationServer.ObtainLocationReportResponse response;

        while (true) {
            LocationServer.ObtainLocationReportRequest request = this.buildObtainLocationReportRequest(userId, ep, currentSeqNumber);

            try {
                response = locationServerServiceStub.withDeadlineAfter(1, TimeUnit.SECONDS).obtainLocationReport(request);
                break;
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode().equals(DEADLINE_EXCEEDED.getCode())) {
                    System.out.println("user" + getClientId() + ": TIMEOUT CLIENT");
                } else {
                    throw e;
                }
            }
        }

        if (response != null && verifyLocationReport(response, currentSeqNumber)) {
            ObjectMapper objectMapper = new ObjectMapper();

            SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), this.privateKey);
            String locationProverContent = Crypto.decryptAES(secretKeySpec, response.getLocationProver().getContent());
            return objectMapper.readValue(locationProverContent, Location.class);
        }
        return null;
    }

    public LocationServer.ObtainLocationReportRequest buildObtainLocationReportRequest(int userId, int ep, int currentSeqNumber) throws GeneralSecurityException {
        LocationReportRequest locationRequest = new LocationReportRequest(userId, ep, 0, 0, this.clientId, currentSeqNumber);
        String requestContent = locationRequest.toJsonString();

        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        return LocationServer.ObtainLocationReportRequest.newBuilder()
                .setKey(Crypto.encryptRSA(Base64.getEncoder().encodeToString(encodedKey), this.serverPublicKey))
                .setContent(Crypto.encryptAES(secretKeySpec, requestContent))
                .setSignature(Crypto.sign(requestContent, this.privateKey))
                .build();
    }

    private void loadPublicKeys() throws GeneralSecurityException {
        for (int i = 0; i < systemInfo.getNumberOfUsers(); i++) {
            String fileName = "client" + i + "-pub.key";
            this.publicKeys.put(i, Crypto.getPub(fileName));
        }
    }

    PublicKey getUserPublicKey(int userId) {
        return publicKeys.get(userId);
    }

    void updateEpochs() {
        Runnable task = () -> {
            getSystemInfo().updateCurrentEp();
            System.out.println("Changed epoch: " + getSystemInfo().getCurrentEp());
        };

        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(task, getSystemInfo().getDurationEp(), getSystemInfo().getDurationEp(), TimeUnit.SECONDS);
    }

    abstract void parseCommand(String cmd) throws GeneralSecurityException, JsonProcessingException;

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
}
