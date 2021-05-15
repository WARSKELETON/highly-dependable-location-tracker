package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.LocationServerServiceGrpc;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.grpc.Status.DEADLINE_EXCEEDED;

abstract class AbstractClient {
    private static final int SERVER_ORIGINAL_PORT = 8080;

    private String serverHost;
    private int serverPort;
    private int clientId;
    private Map<Integer, Integer> seqNumbers;
    private final int maxByzantineUsers;
    private int maxReplicas;
    private int maxByzantineReplicas;
    private PrivateKey privateKey;
    private Map<Integer, PublicKey> serverPublicKeys;
    private Map<Integer, PublicKey> publicKeys;
    private SystemInfo systemInfo;
    private Map<Integer, LocationServerServiceGrpc.LocationServerServiceStub> locationServerServiceStubs;

    AbstractClient(String serverHost, int serverPort, int clientId, SystemInfo systemInfo, int maxByzantineUsers, int maxReplicas, int maxByzantineReplicas) throws GeneralSecurityException, JsonProcessingException {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.clientId = clientId;
        this.systemInfo = systemInfo;
        this.maxByzantineUsers = maxByzantineUsers;
        this.maxReplicas = maxReplicas;
        this.maxByzantineReplicas = maxByzantineReplicas;
        this.loadPublicKeys();
        this.locationServerServiceStubs = new HashMap<>();
        this.connectToServers();
        this.initializeSeqNumbers();
        this.updateEpochs();
    }

    private void initializeSeqNumbers() {
        this.seqNumbers = new HashMap<>();
        for (int i = 0; i < maxReplicas; i++) {
            seqNumbers.put(i, 0);
        }
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

    public int getMaxByzantineUsers() {
        return maxByzantineUsers;
    }

    public int getMaxByzantineReplicas() {
        return maxByzantineReplicas;
    }

    public int getMaxReplicas() {
        return maxReplicas;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    void setPrivateKey(PrivateKey privateKey) {
        this.privateKey = privateKey;
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

    public Map<Integer, Integer> getSeqNumbers() {
        return seqNumbers;
    }

    public Map<Integer, LocationServerServiceGrpc.LocationServerServiceStub> getLocationServerServiceStubs() {
        return locationServerServiceStubs;
    }

    private void connectToServers() {
        for (int i = 0; i < maxReplicas; i++) {
            int port = SERVER_ORIGINAL_PORT + i;
            String target = serverHost + ":" + port;
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            locationServerServiceStubs.put(i, LocationServerServiceGrpc.newStub(channel));
        }
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

    boolean verifyLocationReport(int userId, int ep, LocationServer.ObtainLocationReportResponse response) throws JsonProcessingException, GeneralSecurityException {
        List<Integer> witnessIds = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), this.privateKey);
        if (secretKeySpec == null || response.getLocationProver() == null) return false;

        String locationProverContent = Crypto.decryptAES(secretKeySpec, response.getLocationProver().getContent());
        int receivedSeqNumber = Integer.parseInt(Crypto.decryptAES(secretKeySpec, response.getSeqNumber()));
        int serverId = Integer.parseInt(Crypto.decryptAES(secretKeySpec, response.getServerId()));
        int currentSeqNumber = this.seqNumbers.get(serverId);
        Location locationProver = objectMapper.readValue(locationProverContent, Location.class);
        List<String> locationProofsContent = new ArrayList<>();

        if (locationProver.getEp() != ep || locationProver.getUserId() != userId) {
            System.out.println("user" + getClientId() + ": Server should not be trusted! Received invalid report, either from a different user or different epoch");
            return false;
        }

        if (receivedSeqNumber != currentSeqNumber) {
            System.out.println("user" + getClientId() + ": Server should not be trusted! Received sequence number " + receivedSeqNumber + " but expected " + currentSeqNumber);
            return false;
        }

        for (LocationServer.LocationMessage locationProof : response.getLocationProofsList()) {
            String locationProofContent = Crypto.decryptAES(secretKeySpec, locationProof.getContent());
            LocationProof proof = objectMapper.readValue(locationProofContent, LocationProof.class);
            int witnessId = proof.getWitnessId();
            locationProofsContent.add(locationProofContent);

            // A single illegitimate proof found in the report should invalidate the whole report
            if (!witnessIds.contains(witnessId) && witnessId != locationProver.getUserId() && !(this.verifyLocationProof(proof, locationProver) && Crypto.verify(locationProofContent, locationProof.getSignature(), this.getUserPublicKey(proof.getWitnessId())))) {
                System.out.println("user" + getClientId() + ": Server should not be trusted! Generated illegitimate report for " + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() + ", " + locationProver.getLongitude());
                return false;
            } else {
                witnessIds.add(witnessId);
            }
        }

        if (witnessIds.size() < maxByzantineUsers) {
            System.out.println("user" + getClientId() + ": Server should not be trusted! Generated illegitimate report for " + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() + ", " + locationProver.getLongitude() + ". Insufficient proofs.");
            return false;
        }

        // Verify inner report client signature and outer server signature
        String reportContentString = locationProverContent + locationProofsContent.stream().reduce("", String::concat);
        String responseContentString = reportContentString + receivedSeqNumber + serverId;
        if (!Crypto.verify(reportContentString, response.getLocationProver().getSignature(), this.getUserPublicKey(locationProver.getUserId())) || !Crypto.verify(responseContentString, response.getServerSignature(), this.getServerPublicKey(serverId))) {
            System.out.println("user" + getClientId() + ": Server should not be trusted! Generated illegitimate report for " + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() + ", " + locationProver.getLongitude());
            return false;
        }

        return true;
    }

    private boolean isValidSeqNumber(LocationServer.ObtainLatestSeqNumberResponse response) throws GeneralSecurityException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), this.privateKey);
        String requestContent = Crypto.decryptAES(secretKeySpec, response.getContent());
        SeqNumberMessage seqNumberResponse = objectMapper.readValue(requestContent, SeqNumberMessage.class);
        int clientId = seqNumberResponse.getClientId();
        int serverId = seqNumberResponse.getServerId();
        int seqNumber = seqNumberResponse.getSeqNumber();

        if (!Crypto.verify(requestContent, response.getSignature(), this.getServerPublicKey(serverId)) || clientId != getClientId()) {
            System.out.println("user" + getClientId() + ": invalid sequence number response");
            return false;
        } else {
            this.seqNumbers.put(serverId, seqNumber);
            System.out.println("Seq number with server" + serverId + ": " + this.seqNumbers.get(serverId));
            return true;
        }
    }

    private LocationServer.ObtainLatestSeqNumberRequest buildObtainLatestSeqNumberRequest(int serverId) throws GeneralSecurityException {
        SeqNumberMessage seqNumberMessage = new SeqNumberMessage(clientId, serverId, -1);
        String requestContent = seqNumberMessage.toJsonString();

        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        return LocationServer.ObtainLatestSeqNumberRequest.newBuilder()
                .setKey(Crypto.encryptRSA(Base64.getEncoder().encodeToString(encodedKey), this.getServerPublicKey(serverId)))
                .setContent(Crypto.encryptAES(secretKeySpec, requestContent))
                .setSignature(Crypto.sign(requestContent, this.privateKey))
                .build();
    }

    public void checkLatestSeqNumberRegular() throws GeneralSecurityException, InterruptedException {
        final CountDownLatch finishLatch = new CountDownLatch(maxReplicas);

        Consumer<LocationServer.ObtainLatestSeqNumberResponse> requestObserver = new Consumer<>() {
            @Override
            public void accept(LocationServer.ObtainLatestSeqNumberResponse response) {
                synchronized (finishLatch) {
                    if (finishLatch.getCount() == 0) return;

                    try {
                        if (isValidSeqNumber(response)) {
                            finishLatch.countDown();
                        }
                    } catch (JsonProcessingException | GeneralSecurityException ex) {
                        System.err.println("user" + getClientId() + ": caught processing exception while checking sequence number");
                    }
                }
            }
        };

        for (int serverId : locationServerServiceStubs.keySet()) {
            LocationServer.ObtainLatestSeqNumberRequest request = buildObtainLatestSeqNumberRequest(serverId);
            checkLatestSeqNumber(locationServerServiceStubs.get(serverId), request, requestObserver);
        }

        finishLatch.await(10, TimeUnit.SECONDS);

        if (finishLatch.getCount() > 0) {
            checkLatestSeqNumberRegular();
        }
    }

    private void checkLatestSeqNumber(LocationServerServiceGrpc.LocationServerServiceStub locationServerServiceStub, LocationServer.ObtainLatestSeqNumberRequest request, Consumer<LocationServer.ObtainLatestSeqNumberResponse> callback) {
        try {
            locationServerServiceStub.withDeadlineAfter(1, TimeUnit.SECONDS).obtainLatestSeqNumber(request, new StreamObserver<>() {
                @Override
                public void onNext(LocationServer.ObtainLatestSeqNumberResponse response) {
                    callback.accept(response);
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onCompleted() {
                }
            });
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode().equals(DEADLINE_EXCEEDED.getCode())) {
                System.out.println("user" + getClientId() + ": TIMEOUT CLIENT");
            } else {
                throw e;
            }
        }
    }

    public void generateProofOfWork(SubmitLocationReportRequestHeader header, String content) throws GeneralSecurityException {
        byte[] hash;
        int counter = 0;
        while (true) {
            hash = Crypto.getSHA256Hash(content + counter);

            if (hash[0] == 0) {
                break;
            }

            counter++;
        }

        System.out.println("user" + getClientId() + ": Finished generating proof of work got counter " + counter + " and " + Base64.getEncoder().encodeToString(hash));
        header.setProofOfWork(Base64.getEncoder().encodeToString(hash));
        header.setCounter(counter);
    }

    private LocationServer.SubmitLocationReportRequest buildSubmitLocationReportRequestWriteBack(LocationServer.ObtainLocationReportResponse response, int serverId) throws GeneralSecurityException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        List<LocationServer.LocationMessage> locationProofMessages = new ArrayList<>();
        Map<Integer, String> locationProofsContent = new HashMap<>();
        SecretKeySpec receivedSecretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), getPrivateKey());
        String locationProverContent = Crypto.decryptAES(receivedSecretKeySpec, response.getLocationProver().getContent());
        SubmitLocationReportRequestHeader header = new SubmitLocationReportRequestHeader(this.getClientId(), serverId, "", 0);

        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        for (LocationServer.LocationMessage locationProof : response.getLocationProofsList()) {
            String locationProofContent = Crypto.decryptAES(receivedSecretKeySpec, locationProof.getContent());
            LocationProof proof = objectMapper.readValue(locationProofContent, LocationProof.class);
            int witnessId = proof.getWitnessId();

            locationProofsContent.put(witnessId, locationProofContent);
            locationProofMessages.add(
                    LocationServer.LocationMessage.newBuilder()
                            .setContent(Crypto.encryptAES(secretKeySpec, locationProofContent))
                            .setSignature(locationProof.getSignature())
                            .build()
            );
        }

        String content = locationProverContent;
        String requestContent = content + locationProofsContent.values().stream().reduce("", String::concat) + secretKeySpec.toString() + header.getServerId() + header.getClientId();
        generateProofOfWork(header, requestContent);
        String requestSignatureContent = requestContent + header.getProofOfWork() + header.getCounter();

        return LocationServer.SubmitLocationReportRequest.newBuilder()
                .setKey(Crypto.encryptRSA(Base64.getEncoder().encodeToString(encodedKey), this.getServerPublicKey(serverId)))
                .setLocationProver(
                        LocationServer.LocationMessage.newBuilder()
                                .setContent(Crypto.encryptAES(secretKeySpec, locationProverContent))
                                .setSignature(response.getLocationProver().getSignature())
                                .build())
                .addAllLocationProofs(locationProofMessages)
                .setHeader(Crypto.encryptAES(secretKeySpec, header.toJsonString()))
                .setRequestSignature(Crypto.sign(requestSignatureContent, this.privateKey))
                .build();
    }

    void writeBackLocationReport(LocationServer.ObtainLocationReportResponse response) throws GeneralSecurityException, JsonProcessingException, InterruptedException {

        final CountDownLatch finishLatch = new CountDownLatch((getMaxReplicas() + getMaxByzantineReplicas()) / 2 + 1);

        Consumer<LocationServer.SubmitLocationReportResponse> requestOnSuccessObserver = new Consumer<>() {
            @Override
            public void accept(LocationServer.SubmitLocationReportResponse response) {
                synchronized (finishLatch) {
                    if (finishLatch.getCount() == 0) return;

                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), getPrivateKey());
                        String responseString = Crypto.decryptAES(secretKeySpec, response.getContent());
                        SubmitLocationReportResponse submitResponse = objectMapper.readValue(responseString, SubmitLocationReportResponse.class);
                        String signatureString = responseString + secretKeySpec.toString();

                        if (Crypto.verify(signatureString, response.getSignature(), getServerPublicKey(submitResponse.getServerId()))) {
                            System.out.println("user" + getClientId() + ": Received submit location report response -> " + submitResponse.isOk());
                            finishLatch.countDown();
                        }
                    } catch (GeneralSecurityException | JsonProcessingException ex) {
                        System.err.println("user" + getClientId() + ": caught processing exception while writing back report");
                    }
                }
            }
        };

        Consumer<Throwable> requestOnErrorObserver = new Consumer<>() {
            @Override
            public void accept(Throwable throwable) {
                synchronized (finishLatch) {
                    if (finishLatch.getCount() == 0) return;

                    if (throwable.getMessage().contains("ALREADY_EXISTS")) {
                        finishLatch.countDown();
                    }
                    System.out.println(throwable.getMessage());
                }
            }
        };

        for (int serverId : locationServerServiceStubs.keySet()) {
            LocationServer.SubmitLocationReportRequest request = this.buildSubmitLocationReportRequestWriteBack(response, serverId);
            submitLocationReport(locationServerServiceStubs.get(serverId), request, requestOnSuccessObserver, requestOnErrorObserver);
        }

        finishLatch.await(5, TimeUnit.SECONDS);

        if (finishLatch.getCount() == 0) {
            ObjectMapper objectMapper = new ObjectMapper();

            SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), getPrivateKey());
            String locationProverContent = Crypto.decryptAES(secretKeySpec, response.getLocationProver().getContent());
            Location locationProver = objectMapper.readValue(locationProverContent, Location.class);
            System.out.println("user" + getClientId() + ": Legitimate report! User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() + ", " + locationProver.getLongitude());
        } else {
            writeBackLocationReport(response);
        }
    }

    public void submitLocationReport(LocationServerServiceGrpc.LocationServerServiceStub locationServerServiceStub, LocationServer.SubmitLocationReportRequest request, Consumer<LocationServer.SubmitLocationReportResponse> callbackOnSuccess, Consumer<Throwable> callbackOnError) {
        try {
            locationServerServiceStub.withDeadlineAfter(1, TimeUnit.SECONDS).submitLocationReport(request, new StreamObserver<>() {
                @Override
                public void onNext(LocationServer.SubmitLocationReportResponse response) {
                    callbackOnSuccess.accept(response);
                }

                @Override
                public void onError(Throwable t) {
                    callbackOnError.accept(t);
                }

                @Override
                public void onCompleted() {
                }
            });
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode().equals(DEADLINE_EXCEEDED.getCode())) {
                System.out.println("user" + getClientId() + ": TIMEOUT CLIENT");
            } else {
                throw e;
            }
        }
    }

    Location obtainLocationFromReportResponse(LocationServer.ObtainLocationReportResponse response) throws JsonProcessingException, GeneralSecurityException {
        ObjectMapper objectMapper = new ObjectMapper();
        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), getPrivateKey());
        String locationProverContent = Crypto.decryptAES(secretKeySpec, response.getLocationProver().getContent());

        return objectMapper.readValue(locationProverContent, Location.class);
    }

    public Location obtainLocationReportAtomic(int userId, int ep) throws GeneralSecurityException, InterruptedException, JsonProcessingException {
        List<LocationServer.ObtainLocationReportResponse> reportResponses = new ArrayList<>();

        final CountDownLatch finishLatch = new CountDownLatch((getMaxReplicas() + getMaxByzantineReplicas()) / 2 + 1);

        Consumer<LocationServer.ObtainLocationReportResponse> requestOnSuccessObserver = new Consumer<>() {
            @Override
            public void accept(LocationServer.ObtainLocationReportResponse response) {
                synchronized (finishLatch) {
                    if (finishLatch.getCount() == 0) return;

                    try {
                        if (response != null && verifyLocationReport(userId, ep, response)) {
                            ObjectMapper objectMapper = new ObjectMapper();
                            SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), getPrivateKey());
                            int serverId = Integer.parseInt(Crypto.decryptAES(secretKeySpec, response.getServerId()));
                            String locationProverContent = Crypto.decryptAES(secretKeySpec, response.getLocationProver().getContent());
                            Location locationProver = objectMapper.readValue(locationProverContent, Location.class);

                            reportResponses.add(response);
                            while (finishLatch.getCount() > 0) {
                                finishLatch.countDown();
                            }
                        }
                    } catch (JsonProcessingException | GeneralSecurityException ex) {
                        System.err.println("user" + getClientId() + ": caught processing exception while obtaining report");
                    }
                }
            }
        };

        Consumer<Throwable> requestOnErrorObserver = new Consumer<>() {
            @Override
            public void accept(Throwable throwable) {
                synchronized (finishLatch) {
                    if (finishLatch.getCount() == 0) return;

                    if (throwable.getMessage().contains("NOT_FOUND")) {
                        finishLatch.countDown();
                    }
                    System.out.println(throwable.getMessage());
                }
            }
        };

        for (int serverId : locationServerServiceStubs.keySet()) {
            LocationServer.ObtainLocationReportRequest request = this.buildObtainLocationReportRequest(userId, ep, this.seqNumbers.get(serverId), serverId);
            obtainLocationReport(locationServerServiceStubs.get(serverId), request, requestOnSuccessObserver, requestOnErrorObserver);
            seqNumbers.put(serverId, seqNumbers.get(serverId) + 1);
        }

        finishLatch.await(5, TimeUnit.SECONDS);

        if (finishLatch.getCount() == 0 && !reportResponses.isEmpty()) {
            writeBackLocationReport(reportResponses.get(0));
        } else {
            if (finishLatch.getCount() == 0 && reportResponses.isEmpty()) {
                return null;
            } else {
                obtainLocationReportAtomic(userId, ep);
            }
        }

        return obtainLocationFromReportResponse(reportResponses.get(0));
    }

    public void obtainLocationReport(LocationServerServiceGrpc.LocationServerServiceStub locationServerServiceStub, LocationServer.ObtainLocationReportRequest request, Consumer<LocationServer.ObtainLocationReportResponse> callbackOnSuccess, Consumer<Throwable> callbackOnError) {
        try {
            locationServerServiceStub.withDeadlineAfter(1, TimeUnit.SECONDS).obtainLocationReport(request, new StreamObserver<>() {
                @Override
                public void onNext(LocationServer.ObtainLocationReportResponse response) {
                    callbackOnSuccess.accept(response);
                }

                @Override
                public void onError(Throwable t) {
                    callbackOnError.accept(t);
                }

                @Override
                public void onCompleted() {
                }
            });
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode().equals(DEADLINE_EXCEEDED.getCode())) {
                System.out.println("user" + getClientId() + ": TIMEOUT CLIENT");
            } else {
                throw e;
            }
        }
    }

    public LocationServer.ObtainLocationReportRequest buildObtainLocationReportRequest(int userId, int ep, int currentSeqNumber, int serverId) throws GeneralSecurityException {
        LocationReportRequest locationRequest = new LocationReportRequest(userId, ep, 0, 0, this.clientId, currentSeqNumber);
        String requestContent = locationRequest.toJsonString();

        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        return LocationServer.ObtainLocationReportRequest.newBuilder()
                .setKey(Crypto.encryptRSA(Base64.getEncoder().encodeToString(encodedKey), this.getServerPublicKey(serverId)))
                .setContent(Crypto.encryptAES(secretKeySpec, requestContent))
                .setSignature(Crypto.sign(requestContent, this.privateKey))
                .build();
    }

    private void loadPublicKeys() throws GeneralSecurityException {
        this.publicKeys = new HashMap<>();
        this.serverPublicKeys = new HashMap<>();

        for (int i = 0; i < systemInfo.getNumberOfUsers(); i++) {
            String fileName = "client" + i + "-pub.key";
            this.publicKeys.put(i, Crypto.getPub(fileName));
        }

        for (int i = 0; i < maxReplicas; i++) {
            String fileName = "server" + i + "-pub.key";
            this.serverPublicKeys.put(i, Crypto.getPub(fileName));
        }
    }

    PublicKey getServerPublicKey(int serverId) {
        return serverPublicKeys.get(serverId);
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
        } catch (Exception e2) {
            Logger.getLogger(Client.class.getName()).log(Level.WARNING, e2.getMessage(), e2);
            Thread.currentThread().interrupt();
        } finally {
            System.out.println("> Closing");
        }
    }

    public void cleanSeqNumbers() {
        //seqNumbers.clear();
    }
}
