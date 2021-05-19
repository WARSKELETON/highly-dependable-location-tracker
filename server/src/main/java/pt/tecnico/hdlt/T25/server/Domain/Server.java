package pt.tecnico.hdlt.T25.server.Domain;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Empty;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.javatuples.Pair;
import pt.tecnico.hdlt.T25.ByzantineReliableBroadcast;
import pt.tecnico.hdlt.T25.ByzantineReliableBroadcastServiceGrpc;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.crypto.Crypto;
import pt.tecnico.hdlt.T25.server.Domain.Exceptions.*;
import pt.tecnico.hdlt.T25.server.Services.ByzantineReliableBroadcastServiceImpl;
import pt.tecnico.hdlt.T25.server.Services.LocationServerServiceImpl;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static java.util.Base64.getEncoder;

public class Server {
    private String SERVER_RECOVERY_FILE_PATH;
    private String BACKUP_RECOVERY_FILE_PATH;
    private static final int SERVER_ORIGINAL_PORT = 8080;

    private static final int PUZZLE_DIFFICULTY = 2;

    private int id;
    private int port;
    private int numberOfUsers;
    private int step;
    private int maxByzantineUsers;
    private int maxNearbyByzantineUsers;
    private int maxReplicas;
    private int maxByzantineReplicas;

    private Map<Integer, Boolean> sentEcho;
    private Map<Integer, Boolean> sentReady;
    private Map<Integer, CountDownLatch> delivered;
    private Map<Integer, Map<Integer, ByzantineReliableBroadcast.RequestMsg>> echos;
    private Map<Integer, Map<Integer, ByzantineReliableBroadcast.ReadyMsg>> readys;

    private io.grpc.Server server;
    private final PrivateKey privateKey;
    private Map<Integer, Integer> seqNumbers;
    private Map<Integer, PublicKey> clientPublicKeys;
    private Map<Integer, PublicKey> serverPublicKeys;
    private Map<Integer, ByzantineReliableBroadcastServiceGrpc.ByzantineReliableBroadcastServiceStub> brbStubs;
    private Map<Pair<Integer, Integer>, LocationReport> locationReports; // <UserId, Epoch> to Location Report

    public Server(int serverId, int numberOfUsers, int step, int maxByzantineUsers, int maxNearbyByzantineUsers, int maxReplicas, int maxByzantineReplicas, String keystorePassword, boolean isTest) throws IOException, GeneralSecurityException, InterruptedException {
        this.id = serverId;
        this.SERVER_RECOVERY_FILE_PATH = "resources/server_state" + serverId + ".json";
        this.BACKUP_RECOVERY_FILE_PATH = "resources/backup_state" + serverId + ".json";
        this.port = SERVER_ORIGINAL_PORT + serverId;
        this.numberOfUsers = numberOfUsers;
        this.step = step;
        this.maxByzantineUsers = maxByzantineUsers;
        this.maxNearbyByzantineUsers = maxNearbyByzantineUsers;
        this.maxReplicas = maxReplicas;
        this.maxByzantineReplicas = maxByzantineReplicas;
        this.initializeSeqNumbers();
        this.initializeBrb();
        this.locationReports = new ConcurrentHashMap<>();
        this.privateKey = Crypto.getPriv("server" + this.id + ".jks", "server" + this.id,  keystorePassword);
        this.loadPublicKeys();
        this.loadPreviousState();
        this.startServer();
        this.connectToServers();
        if (!isTest) server.awaitTermination();
    }

    PrivateKey getPrivateKey() {
        return privateKey;
    }

    public int getId() {
        return id;
    }

    private void connectToServers() {
        brbStubs = new ConcurrentHashMap<>();
        for (int i = 0; i < maxReplicas; i++) {
            int port = SERVER_ORIGINAL_PORT + i;
            String target = "localhost:" + port;
            final ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            ByzantineReliableBroadcastServiceGrpc.ByzantineReliableBroadcastServiceStub stub = ByzantineReliableBroadcastServiceGrpc.newStub(channel);
            brbStubs.put(i, stub);
        }
    }

    private void initializeBrb() {
        this.sentEcho = new ConcurrentHashMap<>();
        this.sentReady = new ConcurrentHashMap<>();
        this.delivered = new ConcurrentHashMap<>();
        this.echos = new ConcurrentHashMap<>();
        this.readys = new ConcurrentHashMap<>();

        for (int i = 0; i < numberOfUsers; i++) {
            sentEcho.put(i, false);
            sentReady.put(i, false);
            delivered.put(i, new CountDownLatch(1));
            echos.put(i, new ConcurrentHashMap<>());
            readys.put(i, new ConcurrentHashMap<>());
        }
    }

    void cleanBrb(int userId) {
        sentEcho.put(userId, false);
        sentReady.put(userId, false);
        delivered.put(userId, new CountDownLatch(1));
        echos.put(userId, new ConcurrentHashMap<>());
        readys.put(userId, new ConcurrentHashMap<>());
    }

    private void initializeSeqNumbers() {
        this.seqNumbers = new ConcurrentHashMap<>();
        for (int i = -1; i < numberOfUsers; i++) {
            seqNumbers.put(i, 0);
        }
    }

    private void loadPreviousState() {
        try {
            recoverServerState(SERVER_RECOVERY_FILE_PATH);
            System.out.println("Server" + this.id + ": Recovered previous state successfully.");
        }
        catch (IOException|NullPointerException e) {
            try {
                recoverServerState(BACKUP_RECOVERY_FILE_PATH);
                System.out.println("Server" + this.id + ": Recovered backup previous state successfully.");
            } catch (IOException|NullPointerException e1) {
                System.out.println("Server" + this.id + ": Failed to parse previous state.");
            }
        }
    }

    private void recoverServerState(final String filepath) throws IOException {
        JsonFactory jsonFactory = new JsonFactory();
        ObjectMapper objectMapper = new ObjectMapper(jsonFactory);

        JsonNode objectNode = objectMapper.readTree(new File(filepath));
        objectMapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);

        id = objectNode.get("id").asInt();
        numberOfUsers = objectNode.get("numberOfUsers").asInt();
        step = objectNode.get("step").asInt();
        maxByzantineUsers = objectNode.get("maxByzantineUsers").asInt();
        maxNearbyByzantineUsers = objectNode.get("maxNearbyByzantineUsers").asInt();
        maxReplicas = objectNode.get("maxReplicas").asInt();
        maxByzantineReplicas = objectNode.get("maxByzantineReplicas").asInt();
        JsonNode arrayNode = objectNode.get("locationReports");
        JsonNode seqArrayNode = objectNode.get("seqNumbers");

        for (JsonNode jsonNode : arrayNode) {
            int userId = jsonNode.get("userId").asInt();
            int epoch = jsonNode.get("epoch").asInt();
            String res = jsonNode.get("content").asText();

            LocationReport locationReport = objectMapper.readValue(res, LocationReport.class);

            locationReports.put(new Pair<>(userId, epoch), locationReport);
        }

        for (JsonNode jsonNode : seqArrayNode) {
            int userId = jsonNode.get("userId").asInt();
            int seqNumber = jsonNode.get("seqNumber").asInt();

            seqNumbers.put(userId, seqNumber);
        }
    }

    public void startServer() throws IOException, InterruptedException {
        final BindableService locationService = new LocationServerServiceImpl(this);
        final BindableService brbService = new ByzantineReliableBroadcastServiceImpl(this);

        server = ServerBuilder.forPort(port)
                .addService(locationService)
                .addService(brbService)
                .build();

        server.start();

        System.out.println("Server" + this.id + ": Server started");
    }

    public void shutdownServer() {
        server.shutdown();

        System.out.println("Server" + this.id + ": Server shutdown");
    }

    private PublicKey getServerPublicKey(int serverId) {
        return serverPublicKeys.get(serverId);
    }

    private PublicKey getUserPublicKey(int userId) {
        return clientPublicKeys.get(userId);
    }

    private void loadPublicKeys() throws GeneralSecurityException {
        this.clientPublicKeys = new ConcurrentHashMap<>();
        this.serverPublicKeys = new ConcurrentHashMap<>();

        for (int i = 0; i < this.numberOfUsers; i++) {
            String fileName = "client" + i + "-pub.key";
            this.clientPublicKeys.put(i, Crypto.getPub(fileName));
        }
        this.clientPublicKeys.put(-1, Crypto.getPub("ha-pub.key"));

        for (int i = 0; i < this.maxReplicas; i++) {
            String fileName = "server" + i + "-pub.key";
            this.serverPublicKeys.put(i, Crypto.getPub(fileName));
        }
    }

    private Integer getMapKey(int userId, Map<Integer, Boolean> map) {
        Integer mapKey = null;
        for (Integer clientId : map.keySet()) {
            if (clientId == userId) {
                mapKey = clientId;
            }
        }
        return mapKey;
    }

    private Integer getMapKeyFromDelivered(int userId, Map<Integer, CountDownLatch> map) {
        Integer mapKey = null;
        for (Integer clientId : map.keySet()) {
            if (clientId == userId) {
                mapKey = clientId;
            }
        }
        return mapKey;
    }

    public LocationServer.ObtainLatestSeqNumberResponse obtainLatestSeqNumber(LocationServer.ObtainLatestSeqNumberRequest request) throws GeneralSecurityException, InvalidSignatureException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(request.getKey(), this.privateKey);
        String requestContent = Crypto.decryptAES(secretKeySpec, request.getContent());
        SeqNumberMessage seqNumberMessage = objectMapper.readValue(requestContent, SeqNumberMessage.class);

        int clientId = seqNumberMessage.getClientId();

        if (!Crypto.verify(requestContent, request.getSignature(), this.getUserPublicKey(clientId))) {
            System.out.println("Server" + this.id + ": Some user tried to illegitimately request user" + clientId + " sequence number");
            throw new InvalidSignatureException();
        }

        seqNumbers.putIfAbsent(clientId, 0);
        int currentSeqNumber = seqNumbers.get(clientId);

        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec newSecretKeySpec = new SecretKeySpec(encodedKey, "AES");
        SeqNumberMessage seqNumberResponse = new SeqNumberMessage(clientId, this.id, currentSeqNumber);

        return LocationServer.ObtainLatestSeqNumberResponse.newBuilder()
                .setKey(Crypto.encryptRSA(getEncoder().encodeToString(encodedKey), this.getUserPublicKey(clientId)))
                .setContent(Crypto.encryptAES(newSecretKeySpec, seqNumberResponse.toJsonString()))
                .setSignature(Crypto.sign(seqNumberResponse.toJsonString(), this.privateKey))
                .build();
    }

    public void handleReady(ByzantineReliableBroadcast.ReadyMsg ready) throws GeneralSecurityException, IOException, InvalidSignatureException {
        ObjectMapper objectMapper = new ObjectMapper();
        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(ready.getKey(), this.privateKey);
        int serverIdSender = Integer.parseInt(Crypto.decryptAES(secretKeySpec, ready.getServerIdSender()));
        String locationProverContent = Crypto.decryptAES(secretKeySpec, ready.getLocationProver().getContent());
        Location locationProver = objectMapper.readValue(locationProverContent, Location.class);
        int sourceClientId = locationProver.getUserId();

        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();

        // Check each location proof in the report
        for (ByzantineReliableBroadcast.LocationProofMsg locationProof : ready.getLocationProofsList()) {
            String locationProofContent = Crypto.decryptAES(secretKeySpec, locationProof.getContent());
            int locationProofServerId = Integer.parseInt(Crypto.decryptAES(secretKeySpec, locationProof.getServerId()));

            LocationProof proof = objectMapper.readValue(locationProofContent, LocationProof.class);
            int witnessId = proof.getWitnessId();

            locationProofsContent.put(witnessId, locationProofContent);
            locationProofsSignatures.put(witnessId, locationProof.getWitnessSignature());
            String serverSignatureContent = locationProofContent + serverIdSender;

            // Verify server proof signature
            if (locationProofServerId != serverIdSender || !Crypto.verify(serverSignatureContent, locationProof.getServerSignature(), this.getServerPublicKey(serverIdSender))) {
                System.out.println("Server" + this.id + ": Report failed integrity or authentication checks in ready! User" + sourceClientId + " ep " + locationProver.getEp() + " coords " + locationProver.getLatitude() + ", " + locationProver.getLongitude());
                throw new InvalidSignatureException();
            }
        }

        String reportContent = locationProverContent + locationProofsContent.values().stream().reduce("", String::concat);
        String signatureContent = reportContent + secretKeySpec.toString() + serverIdSender + this.id;
        if (!Crypto.verify(signatureContent, ready.getSignature(), this.getServerPublicKey(serverIdSender))) {
            System.out.println("Server" + this.id + ": Report failed integrity or authentication checks in echo! User" + sourceClientId + " ep " + locationProver.getEp() + " coords " + locationProver.getLatitude() + ", " + locationProver.getLongitude());
            throw new InvalidSignatureException();
        }

        System.out.println("Server" + this.id + ": Received a ready from server" + serverIdSender + ". User" + sourceClientId + " ep " + locationProver.getEp() + " coords " + locationProver.getLatitude() + ", " + locationProver.getLongitude());
        Integer readykey = getMapKey(sourceClientId, sentReady);
        Integer deliveredKey = getMapKeyFromDelivered(sourceClientId, delivered);

        synchronized (readykey) {
            Map<Integer, ByzantineReliableBroadcast.ReadyMsg> readysReceived = readys.get(readykey);
            readysReceived.put(serverIdSender, ready);
            // Have not send ready yet and Got a ready for at least a correct replica -> send Ready
            if (!sentReady.get(readykey) && readyMatches(reportContent, sourceClientId) > maxByzantineReplicas) {
                System.out.println("Server" + this.id + ": Received f + 1 readys and have not send ready... Broadcasting Ready! User" + sourceClientId + " ep " + locationProver.getEp() + " coords " + locationProver.getLatitude() + ", " + locationProver.getLongitude());
                sentReady.put(readykey, true);
                sendReady(locationProverContent, ready.getLocationProver().getSignature(), locationProofsContent, locationProofsSignatures);
            }

            synchronized (deliveredKey) {
                // Have not delivered and got a byzantine quorum of readys -> deliver
                if (delivered.get(deliveredKey).getCount() > 0 && readyMatches(reportContent, sourceClientId) > 2 * maxByzantineReplicas) {
                    System.out.println("Server" + this.id + ": Received a byzantine quorum of readys and have not delivered... Delivering! User" + sourceClientId + " ep " + locationProver.getEp() + " coords " + locationProver.getLatitude() + ", " + locationProver.getLongitude());
                    delivered.get(deliveredKey).countDown();
                }
            }
        }
    }

    private ByzantineReliableBroadcast.ReadyMsg buildReadyRequest(String locationProverContent, String locationProverSignature, Map<Integer, String> locationProofsContent, Map<Integer, String> locationProofsSignatures, int serverIdReceiver) throws GeneralSecurityException {
        List<ByzantineReliableBroadcast.LocationProofMsg> locationProofMessages = new ArrayList<>();
        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        for (Integer witnessId : locationProofsContent.keySet()) {
            String locationProofContent = locationProofsContent.get(witnessId);
            String serverSignatureContent = locationProofContent + this.id;

            locationProofMessages.add(
                    ByzantineReliableBroadcast.LocationProofMsg.newBuilder()
                            .setContent(Crypto.encryptAES(secretKeySpec, locationProofContent))
                            .setWitnessSignature(locationProofsSignatures.get(witnessId))
                            .setServerId(Crypto.encryptAES(secretKeySpec, String.valueOf(this.id)))
                            .setServerSignature(Crypto.sign(serverSignatureContent, getPrivateKey()))
                            .build()
            );
        }

        String serverSignature = locationProverContent + locationProofsContent.values().stream().reduce("", String::concat) + secretKeySpec.toString() + this.id + serverIdReceiver;
        return ByzantineReliableBroadcast.ReadyMsg.newBuilder()
                .setKey(Crypto.encryptRSA(getEncoder().encodeToString(encodedKey), this.getServerPublicKey(serverIdReceiver)))
                .setLocationProver(
                        ByzantineReliableBroadcast.LocationMsg.newBuilder()
                                .setContent(Crypto.encryptAES(secretKeySpec, locationProverContent))
                                .setSignature(locationProverSignature)
                                .build())
                .addAllLocationProofs(locationProofMessages)
                .setSignature(Crypto.sign(serverSignature, this.privateKey))
                .setServerIdSender(Crypto.encryptAES(secretKeySpec, Integer.toString(this.id)))
                .setServerIdReceiver(Crypto.encryptAES(secretKeySpec, Integer.toString(serverIdReceiver)))
                .build();
    }

    private void sendReady(String locationProverContent, String locationProverSignature, Map<Integer, String> locationProofsContent, Map<Integer, String> locationProofsSignatures) throws GeneralSecurityException {
        for (Integer serverId : brbStubs.keySet()) {
            ByzantineReliableBroadcast.ReadyMsg request = buildReadyRequest(locationProverContent, locationProverSignature, locationProofsContent, locationProofsSignatures, serverId);
            brbStubs.get(serverId).ready(request, new StreamObserver<Empty>() {
                @Override
                public void onNext(Empty empty) {
                }

                @Override
                public void onError(Throwable throwable) {
                    System.out.println("Ready to server" + serverId + " " + throwable.getMessage());
                }

                @Override
                public void onCompleted() {
                }
            });
        }
    }

    private int readyMatches(String receivedSignatureContent, int sourceClientId) {
        int matches = 0;
        Map<Integer, ByzantineReliableBroadcast.ReadyMsg> readysReceived = readys.get(sourceClientId);

        for (Integer serverId : readysReceived.keySet()) {
            ByzantineReliableBroadcast.ReadyMsg request = readysReceived.get(serverId);
            if (Crypto.verify(receivedSignatureContent, request.getLocationProver().getSignature(), this.getUserPublicKey(sourceClientId))) {
                matches++;
            } else {
                System.out.println("Server" + this.id + ": Ready does not match!");
            }
        }

        return matches;
    }

    private int countMatches(String receivedSignatureContent, int sourceClientId, Map<Integer, ByzantineReliableBroadcast.RequestMsg> requestsReceived) {
        int matches = 0;

        for (Integer serverId : requestsReceived.keySet()) {
            ByzantineReliableBroadcast.RequestMsg request = requestsReceived.get(serverId);
            if (Crypto.verify(receivedSignatureContent, request.getLocationProver().getSignature(), this.getUserPublicKey(sourceClientId))) {
                matches++;
            } else {
                System.out.println("Server" + this.id + ": Request does not match!");
            }
        }

        return matches;
    }

    public void handleEcho(ByzantineReliableBroadcast.RequestMsg echo) throws GeneralSecurityException, InvalidSignatureException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(echo.getKey(), this.privateKey);
        int serverIdSender = Integer.parseInt(Crypto.decryptAES(secretKeySpec, echo.getServerIdSender()));
        String locationProverContent = Crypto.decryptAES(secretKeySpec, echo.getLocationProver().getContent());
        Location locationProver = objectMapper.readValue(locationProverContent, Location.class);
        int sourceClientId = locationProver.getUserId();

        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();

        // Check each location proof in the report
        for (ByzantineReliableBroadcast.LocationMsg locationProof : echo.getLocationProofsList()) {
            String locationProofContent = Crypto.decryptAES(secretKeySpec, locationProof.getContent());

            LocationProof proof = objectMapper.readValue(locationProofContent, LocationProof.class);
            int witnessId = proof.getWitnessId();

            locationProofsContent.put(witnessId, locationProofContent);
            locationProofsSignatures.put(witnessId, locationProof.getSignature());
        }

        String reportContent = locationProverContent + locationProofsContent.values().stream().reduce("", String::concat);
        String signatureContent = reportContent + secretKeySpec.toString() + serverIdSender + this.id;
        if (!Crypto.verify(signatureContent, echo.getSignature(), this.getServerPublicKey(serverIdSender))) {
            System.out.println("Server" + this.id + ": Report failed integrity or authentication checks in echo! User" + sourceClientId + " ep " + locationProver.getEp() + " coords " + locationProver.getLatitude() + ", " + locationProver.getLongitude());
            throw new InvalidSignatureException();
        }

        System.out.println("Server" + this.id + ": Received an echo from server" + serverIdSender + ". User" + sourceClientId + " ep " + locationProver.getEp() + " coords " + locationProver.getLatitude() + ", " + locationProver.getLongitude());
        Integer echokey = getMapKey(sourceClientId, sentEcho);
        Integer readykey = getMapKey(sourceClientId, sentReady);

        synchronized (echokey) {
            Map<Integer, ByzantineReliableBroadcast.RequestMsg> echosReceived = echos.get(echokey);
            echosReceived.put(serverIdSender, echo);
            synchronized (readykey) {
                // Have not send ready yet and Byzantine Quorum of Echos -> send Ready
                if (!sentReady.get(readykey) && countMatches(reportContent, sourceClientId, echosReceived) > (maxReplicas + maxByzantineReplicas) / 2) {
                    System.out.println("Server" + this.id + ": Received a byzantine quorum of echos and have not send ready... Broadcasting Ready! User" + sourceClientId + " ep " + locationProver.getEp() + " coords " + locationProver.getLatitude() + ", " + locationProver.getLongitude());
                    sentReady.put(readykey, true);
                    sendReady(locationProverContent, echo.getLocationProver().getSignature(), locationProofsContent, locationProofsSignatures);
                }
            }
        }
    }

    private ByzantineReliableBroadcast.RequestMsg buildEchoRequest(LocationReport report, int serverIdReceiver) throws GeneralSecurityException {
        List<ByzantineReliableBroadcast.LocationMsg> locationProofMessages = new ArrayList<>();
        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        for (Integer witnessId : report.getLocationProofsContent().keySet()) {
            String locationProofContent = report.getLocationProofsContent().get(witnessId);

            locationProofMessages.add(
                    ByzantineReliableBroadcast.LocationMsg.newBuilder()
                            .setContent(Crypto.encryptAES(secretKeySpec, locationProofContent))
                            .setSignature(report.getLocationProofsSignature().get(witnessId))
                            .build()
            );
        }

        String serverSignature = report.getLocationProver().toJsonString() + report.getLocationProofsContent().values().stream().reduce("", String::concat) + secretKeySpec.toString() + this.id + serverIdReceiver;
        return ByzantineReliableBroadcast.RequestMsg.newBuilder()
                .setKey(Crypto.encryptRSA(getEncoder().encodeToString(encodedKey), this.getServerPublicKey(serverIdReceiver)))
                .setLocationProver(
                        ByzantineReliableBroadcast.LocationMsg.newBuilder()
                                .setContent(Crypto.encryptAES(secretKeySpec, report.getLocationProver().toJsonString()))
                                .setSignature(report.getLocationProverSignature())
                                .build())
                .addAllLocationProofs(locationProofMessages)
                .setSignature(Crypto.sign(serverSignature, this.privateKey))
                .setServerIdSender(Crypto.encryptAES(secretKeySpec, Integer.toString(this.id)))
                .setServerIdReceiver(Crypto.encryptAES(secretKeySpec, Integer.toString(serverIdReceiver)))
                .build();
    }

    boolean broadcastReport(LocationReport report) throws GeneralSecurityException, InterruptedException {
        int sourceClientId = report.getLocationProver().getUserId();

        if (!sentEcho.get(sourceClientId)) {
            System.out.println("Server" + this.id + ": Have not send echo... Broadcasting Echo! User" + sourceClientId + " ep " + report.getLocationProver().getEp() + " coords " + report.getLocationProver().getLatitude() + ", " + report.getLocationProver().getLongitude());
            sentEcho.put(sourceClientId, true);

            for (Integer serverId : brbStubs.keySet()) {
                ByzantineReliableBroadcast.RequestMsg request = buildEchoRequest(report, serverId);
                brbStubs.get(serverId).echo(request, new StreamObserver<Empty>() {
                    @Override
                    public void onNext(Empty empty) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        System.out.println("Echo to server" + serverId + " " + throwable.getMessage());
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
            }

            Integer deliverkey = getMapKeyFromDelivered(sourceClientId, delivered);

            delivered.get(deliverkey).await(5, TimeUnit.SECONDS);

            synchronized (deliverkey) {
                return delivered.get(deliverkey).getCount() == 0;
            }
        }

        return false;
    }

    void deliverReport(LocationReport locationReport) throws IOException, DuplicateReportException, GeneralSecurityException {
        ObjectMapper objectMapper = new ObjectMapper();
        Location locationProver = locationReport.getLocationProver();
        boolean locationReportExists = locationReports.get(new Pair<>(locationProver.getUserId(), locationProver.getEp())) != null;

        if (locationReportExists) {
            System.out.println("Server" + this.id + ": Same report already exists, cleaning brb and returning! User" + locationProver.getUserId() + " ep " + locationProver.getEp() + " coords " + locationProver.getLatitude() + ", " + locationProver.getLongitude());
            cleanBrb(locationProver.getUserId());
            throw new DuplicateReportException(locationProver.getUserId(), locationProver.getEp());
        }

        Integer deliverkey = getMapKeyFromDelivered(locationProver.getUserId(), delivered);

        synchronized (deliverkey) {
            Map<Integer, ByzantineReliableBroadcast.ReadyMsg> allReadys = readys.get(locationProver.getUserId());
            Map<Integer, Map<Integer, String>> locationProofsByServer = new HashMap<>();
            Map<Integer, Map<Integer, String>> allLocationProofs = new HashMap<>();

            for (Integer serverId : allReadys.keySet()) {
                ByzantineReliableBroadcast.ReadyMsg readyMsg = allReadys.get(serverId);
                List<ByzantineReliableBroadcast.LocationProofMsg> locationProofMsgs = readyMsg.getLocationProofsList();
                SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(readyMsg.getKey(), this.privateKey);
                Map<Integer, String> locationProofServerSignatures = new HashMap<>();

                for (ByzantineReliableBroadcast.LocationProofMsg locationProofMsg : locationProofMsgs) {
                    String locationProofContent = Crypto.decryptAES(secretKeySpec, locationProofMsg.getContent());
                    LocationProof proof = objectMapper.readValue(locationProofContent, LocationProof.class);

                    locationProofServerSignatures.put(proof.getWitnessId(), locationProofMsg.getServerSignature());
                }

                locationProofsByServer.put(serverId, locationProofServerSignatures);
            }

            for (Integer witnessId : locationReport.getLocationProofsContent().keySet()) {
                Map<Integer, String> locationProofServerSignatures = new HashMap<>();

                for (Integer serverId : allReadys.keySet()) {
                    locationProofServerSignatures.put(serverId, locationProofsByServer.get(serverId).get(witnessId));
                }

                allLocationProofs.put(witnessId, locationProofServerSignatures);
            }

            locationReport.setLocationProofsServerSignature(allLocationProofs);
            System.out.println("Server" + this.id + ": " + locationReport.toJsonString());
        }

        locationReports.put(new Pair<>(locationProver.getUserId(), locationProver.getEp()), locationReport);
        this.saveCurrentServerState();
        cleanBrb(locationProver.getUserId());
    }

    private boolean reportMatchesExistent(LocationReport locationReport) {
        LocationReport existentReport = locationReports.get(new Pair<>(locationReport.getLocationProver().getUserId(), locationReport.getLocationProver().getEp()));

        return existentReport.getLocationProverSignature().equals(locationReport.getLocationProverSignature());
    }

    private boolean verifyProofOfWork(String content, String clientHash, int counter) throws GeneralSecurityException {
        byte[] hashBytes = Crypto.getSHA256Hash(content + counter);
        String hash = Base64.getEncoder().encodeToString(hashBytes);

        int i = 0;
        while (i < PUZZLE_DIFFICULTY) {
            if (hashBytes[i] != 0) return false;
            i++;
        }

        return hash.equals(clientHash);
    }

    public LocationServer.SubmitLocationReportResponse submitLocationReport(LocationServer.SubmitLocationReportRequest report) throws IOException, GeneralSecurityException, DuplicateReportException, InvalidSignatureException, InvalidNumberOfProofsException, InterruptedException, InvalidProofOfWorkException {
        ObjectMapper objectMapper = new ObjectMapper();
        List<Integer> witnessIds = new ArrayList<>();

        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(report.getKey(), this.privateKey);
        String locationProverContent = Crypto.decryptAES(secretKeySpec, report.getLocationProver().getContent());
        String headerContent = Crypto.decryptAES(secretKeySpec, report.getHeader());

        Location locationProver = objectMapper.readValue(locationProverContent, Location.class);
        SubmitLocationReportRequestHeader header = objectMapper.readValue(headerContent, SubmitLocationReportRequestHeader.class);

        int senderClientId = header.getClientId();

        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();

        System.out.println("Server" + this.id + ": Initiating verification of report with location: User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());

        // Check each location proof in the report
        for (LocationServer.LocationMessage locationProof : report.getLocationProofsList()) {
            String locationProofContent = Crypto.decryptAES(secretKeySpec, locationProof.getContent());

            LocationProof proof = objectMapper.readValue(locationProofContent, LocationProof.class);
            int witnessId = proof.getWitnessId();

            locationProofsContent.put(witnessId, locationProofContent);
            locationProofsSignatures.put(witnessId, locationProof.getSignature());

            // Witness must be distinct, different from Prover, the location proof matches prover's location and the signature is correct & authentic (from the witness)
            if (!witnessIds.contains(witnessId) && witnessId != locationProver.getUserId() && this.verifyLocationProof(proof, locationProver) && Crypto.verify(locationProofContent, locationProof.getSignature(), this.getUserPublicKey(witnessId))) {
                witnessIds.add(witnessId);
                System.out.println("Server" + this.id + ": Obtained legitimate witness proof from Witness" + witnessId + " witnessed User" + proof.getUserId() + " at " + proof.getEp() + " " + proof.getLatitude() +  ", " + proof.getLongitude());
            } else {
                System.out.println("Server" + this.id + ": Obtained illegitimate witness proof from Witness" + proof.getWitnessId() + " where User" + proof.getUserId() + " would be at " + proof.getEp() + " " + proof.getLatitude() +  ", " + proof.getLongitude());
            }
        }

        String reportContentString = locationProverContent + locationProofsContent.values().stream().reduce("", String::concat);
        String proofOfWorkContent = reportContentString + secretKeySpec.toString() + header.getServerId() + header.getClientId();
        String requestContentString = proofOfWorkContent + header.getProofOfWork() + header.getCounter();

        if (!verifyProofOfWork(proofOfWorkContent, header.getProofOfWork(), header.getCounter())) {
            System.out.println("Server" + this.id + ": Report failed proof of work checks! User" + locationProver.getUserId() + " would be at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
            throw new InvalidProofOfWorkException();
        }

        // Verify the whole report content and request
        if (!Crypto.verify(reportContentString, report.getLocationProver().getSignature(), this.getUserPublicKey(locationProver.getUserId())) || !Crypto.verify(requestContentString, report.getRequestSignature(), this.getUserPublicKey(senderClientId))) {
            System.out.println("Server" + this.id + ": Report failed integrity or authentication checks! User" + locationProver.getUserId() + " would be at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
            throw new InvalidSignatureException();
        }

        if (witnessIds.size() >= maxByzantineUsers) {
            LocationReport locationReport = new LocationReport(locationProver, report.getLocationProver().getSignature(), locationProofsContent, locationProofsSignatures);
            // Duplicate location reports are deemed illegitimate
            boolean locationReportExists = locationReports.get(new Pair<>(locationProver.getUserId(), locationProver.getEp())) != null;
            if (locationReportExists && !reportMatchesExistent(locationReport)) {
                throw new DuplicateReportException(locationProver.getUserId(), locationProver.getEp());
            }

            if (!broadcastReport(locationReport)) {
                cleanBrb(locationProver.getUserId());
                System.out.println("Server" + this.id + ": Failed to submit report! Location: User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
                return buildSubmitLocationReportResponse(false, locationProver.getUserId());
            }
            deliverReport(locationReport);

            System.out.println("Server" + this.id + ": Report submitted with success! Location: User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
            return buildSubmitLocationReportResponse(true, locationProver.getUserId());
        }
        System.out.println("Server" + this.id + ": Failed to submit report! Location: User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
        throw new InvalidNumberOfProofsException(witnessIds.size(), maxByzantineUsers);
    }

    private boolean verifyLocationProof(LocationProof proof, Location locationProver) {
        return proof.getEp() == locationProver.getEp() &&
                proof.getUserId() == locationProver.getUserId() &&
                proof.getLatitude() == locationProver.getLatitude() &&
                proof.getLongitude() == locationProver.getLongitude();
    }

    public LocationServer.ObtainLocationReportResponse obtainLocationReport(LocationServer.ObtainLocationReportRequest request) throws IOException, GeneralSecurityException, InvalidSignatureException, ReportNotFoundException, StaleException {
        ObjectMapper objectMapper = new ObjectMapper();

        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(request.getKey(), this.privateKey);
        String requestContent = Crypto.decryptAES(secretKeySpec, request.getContent());
        LocationReportRequest locationRequest = objectMapper.readValue(requestContent, LocationReportRequest.class);
        int sourceClientId = locationRequest.getSourceClientId();
        int receivedSeqNumber = locationRequest.getSeqNumber();
        int expectedSeqNumber = seqNumbers.get(sourceClientId);

        if (receivedSeqNumber != expectedSeqNumber) {
            System.out.println("Server" + this.id + ": Sequence numbers do not match! Received " + receivedSeqNumber + " however expected " + expectedSeqNumber);
            throw new StaleException(sourceClientId, receivedSeqNumber, expectedSeqNumber);
        }

        LocationReport locationReport = locationReports.get(new Pair<>(locationRequest.getUserId(), locationRequest.getEp()));
        if (locationReport == null) {
            System.out.println("Server" + this.id + ": Report not found! " + locationRequest.getUserId() + " location at epoch " + locationRequest.getEp());
            throw new ReportNotFoundException(locationRequest.getUserId(), locationRequest.getEp());
        }
        String locationReportContent = locationReport.getLocationProver().toJsonString() + locationReport.getLocationProofsContent().values().stream().reduce("", String::concat);

        if ((!Crypto.verify(locationReportContent, locationReport.getLocationProverSignature(), this.getUserPublicKey(sourceClientId)) && sourceClientId != -1) || !Crypto.verify(requestContent, request.getSignature(), this.getUserPublicKey(sourceClientId))) {
            System.out.println("Server" + this.id + ": Some user tried to illegitimately request user" + locationRequest.getUserId() + " location at epoch " + locationRequest.getEp());
            throw new InvalidSignatureException();
        }

        System.out.println("Server" + this.id + ": Obtaining location for " + locationRequest.getUserId() + " at epoch " + locationRequest.getEp());

        this.saveCurrentServerState();

        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec newSecretKeySpec = new SecretKeySpec(encodedKey, "AES");
        seqNumbers.put(sourceClientId, expectedSeqNumber + 1);
        return this.buildLocationReportResponse(locationReport, sourceClientId, seqNumbers.get(sourceClientId), newSecretKeySpec, encodedKey);
    }

    private LocationServer.ObtainLocationReportResponse buildLocationReportResponse(LocationReport report, int userId, int seqNumber, SecretKeySpec secretKeySpec, byte[] encodedKey) throws GeneralSecurityException, JsonProcessingException {
        List<LocationServer.ProofsContent> locationProofMessages = new ArrayList<>();

        for (Integer witnessId : report.getLocationProofsContent().keySet()) {
            String locationProofContent = report.getLocationProofsContent().get(witnessId);
            Map<Integer, String> serverSignatures = report.getLocationProofsServerSignature().get(witnessId);

            locationProofMessages.add(
                    LocationServer.ProofsContent.newBuilder()
                            .setContent(Crypto.encryptAES(secretKeySpec, locationProofContent))
                            .setWitnessSignature(report.getLocationProofsSignature().get(witnessId))
                            .setServerSignatures(new ObjectMapper().writeValueAsString(serverSignatures))
                            .build()
            );
        }

        String serverSignature = report.getLocationProver().toJsonString() + report.getLocationProofsContent().values().stream().reduce("", String::concat) + seqNumber + this.id;
        return LocationServer.ObtainLocationReportResponse.newBuilder()
                .setKey(Crypto.encryptRSA(getEncoder().encodeToString(encodedKey), this.getUserPublicKey(userId)))
                .setLocationProver(
                        LocationServer.LocationMessage.newBuilder()
                                .setContent(Crypto.encryptAES(secretKeySpec, report.getLocationProver().toJsonString()))
                                .setSignature(report.getLocationProverSignature())
                                .build())
                .addAllLocationProofs(locationProofMessages)
                .setServerSignature(Crypto.sign(serverSignature, this.privateKey))
                .setSeqNumber(Crypto.encryptAES(secretKeySpec, Integer.toString(seqNumber)))
                .setServerId(Crypto.encryptAES(secretKeySpec, Integer.toString(this.id)))
                .build();
    }

    public LocationServer.ObtainUsersAtLocationResponse obtainUsersAtLocation(LocationServer.ObtainUsersAtLocationRequest request) throws IOException, GeneralSecurityException, InvalidSignatureException, StaleException {
        ObjectMapper objectMapper = new ObjectMapper();

        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(request.getKey(), this.privateKey);
        String requestContent = Crypto.decryptAES(secretKeySpec, request.getContent());
        int receivedSeqNumber = Integer.parseInt(Crypto.decryptAES(secretKeySpec, request.getSeqNumber()));
        Location locationRequest = objectMapper.readValue(requestContent, Location.class);
        String expectedSignature = requestContent + secretKeySpec.toString() + receivedSeqNumber + this.id;

        if (!Crypto.verify(expectedSignature, request.getSignature(), this.getUserPublicKey(-1))) {
            System.out.println("Server" + this.id + ": some user tried to illegitimately request users' location at epoch " + locationRequest.getEp() + " " + locationRequest.getLatitude() + ", " + locationRequest.getLongitude());
            throw new InvalidSignatureException();
        }

        int expectedSeqNumber = seqNumbers.get(-1);
        if (receivedSeqNumber != expectedSeqNumber) {
            System.out.println("Server" + this.id + ": Sequence numbers do not match! Received " + receivedSeqNumber + " however expected " + expectedSeqNumber);
            throw new StaleException(-1, receivedSeqNumber, expectedSeqNumber);
        }
        seqNumbers.put(-1, expectedSeqNumber + 1);

        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec newSecretKeySpec = new SecretKeySpec(encodedKey, "AES");

        List<LocationServer.ObtainLocationReportResponse> locationReportResponses = new ArrayList<>();
        List<String> reportSignatures = new ArrayList<>();
        for (int i = 0; i < this.numberOfUsers; i++) {
            LocationReport report = locationReports.get(new Pair<>(i, locationRequest.getEp()));

            if (report != null && report.getLocationProver().getLongitude() == locationRequest.getLongitude() && report.getLocationProver().getLatitude() == locationRequest.getLatitude()) {
                LocationServer.ObtainLocationReportResponse reportResponse = this.buildLocationReportResponse(report, -1, seqNumbers.get(-1), newSecretKeySpec, encodedKey);
                locationReportResponses.add(reportResponse);
                reportSignatures.add(reportResponse.getServerSignature());
            }
        }

        String serverSignature = reportSignatures.stream().reduce("", String::concat) + newSecretKeySpec.toString() + seqNumbers.get(-1) + this.id;
        this.saveCurrentServerState();

        System.out.println("Server" + this.id + ": Sending HAClient reports of users at epoch " + locationRequest.getEp() + " coords " + locationRequest.getLatitude() + ", " + locationRequest.getLongitude());
        return LocationServer.ObtainUsersAtLocationResponse.newBuilder()
                .setKey(Crypto.encryptRSA(getEncoder().encodeToString(encodedKey), this.getUserPublicKey(-1)))
                .addAllLocationReports(locationReportResponses)
                .setServerSignature(Crypto.sign(serverSignature, this.privateKey))
                .setSeqNumber(Crypto.encryptAES(newSecretKeySpec, Integer.toString(seqNumbers.get(-1))))
                .setServerId(Crypto.encryptAES(newSecretKeySpec, Integer.toString(this.id)))
                .build();
    }

    public LocationServer.RequestMyProofsResponse requestMyProofs(LocationServer.RequestMyProofsRequest request) throws IOException, GeneralSecurityException, InvalidSignatureException, StaleException {
        ObjectMapper objectMapper = new ObjectMapper();

        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(request.getKey(), this.privateKey);
        String requestContent = Crypto.decryptAES(secretKeySpec, request.getContent());
        ProofsRequest proofsRequest = objectMapper.readValue(requestContent, ProofsRequest.class);
        int receivedSeqNumber = proofsRequest.getSeqNumber();

        int clientId = proofsRequest.getUserId();

        int expectedSeqNumber = seqNumbers.get(clientId);

        if (receivedSeqNumber != expectedSeqNumber) throw new StaleException(clientId, receivedSeqNumber, expectedSeqNumber);

        seqNumbers.put(clientId, expectedSeqNumber + 1);

        if (!Crypto.verify(requestContent + secretKeySpec.toString(), request.getSignature(), this.getUserPublicKey(clientId))) {
            System.out.println("Server" + this.id + ": user" + clientId + " tried to illegitimately request users' proofs at certain epochs");
            throw new InvalidSignatureException();
        }

        return buildRequestMyProofsResponse(proofsRequest, clientId, seqNumbers.get(clientId));
    }

    private LocationServer.RequestMyProofsResponse buildRequestMyProofsResponse(ProofsRequest proofsRequest, int userId, int seqNumber) throws GeneralSecurityException, JsonProcessingException {
        List<LocationServer.ProofsContent> locationProofMessages = new ArrayList<>();
        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        // Add only its own proofs to the list
        for (LocationReport locationReport : locationReports.values()) {
            if (!proofsRequest.getEps().contains(locationReport.getLocationProver().getEp())) continue;

            for (Integer witnessId : locationReport.getLocationProofsContent().keySet()) {
                if (witnessId != proofsRequest.getUserId()) continue;

                System.out.println("Server" + this.id + ": user" + userId + " proved user" + locationReport.getLocationProver().getUserId() + " location ( " + locationReport.getLocationProver().getLatitude() + ", " + locationReport.getLocationProver().getLongitude() + " ) at " + locationReport.getLocationProver().getEp());

                String locationProofContent = locationReport.getLocationProofsContent().get(witnessId);

                Map<Integer, String> serverSignatures = locationReport.getLocationProofsServerSignature().get(userId);

                locationProofMessages.add(
                        LocationServer.ProofsContent.newBuilder()
                                .setContent(Crypto.encryptAES(secretKeySpec, locationProofContent))
                                .setWitnessSignature(locationReport.getLocationProofsSignature().get(witnessId))
                                .setServerSignatures(new ObjectMapper().writeValueAsString(serverSignatures))
                                .build()
                );
            }
        }

        String serverSignature = locationProofMessages.stream().map(AbstractMessage::toString).reduce("", String::concat) + seqNumber + this.id;
        return LocationServer.RequestMyProofsResponse.newBuilder()
                .setKey(Crypto.encryptRSA(Base64.getEncoder().encodeToString(encodedKey), this.getUserPublicKey(userId)))
                .addAllLocationProofs(locationProofMessages)
                .setServerSignature(Crypto.sign(serverSignature, this.privateKey))
                .setSeqNumber(Crypto.encryptAES(secretKeySpec, Integer.toString(seqNumber)))
                .setServerId(Crypto.encryptAES(secretKeySpec, Integer.toString(this.id)))
                .build();
    }

    LocationServer.SubmitLocationReportResponse buildSubmitLocationReportResponse(boolean verified, int userId) throws GeneralSecurityException {
        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");
        SubmitLocationReportResponse response = new SubmitLocationReportResponse(userId, this.id, verified);
        String responseString = response.toJsonString();
        String responseSignatureString = response.toJsonString() + secretKeySpec.toString();

        return LocationServer.SubmitLocationReportResponse.newBuilder()
                .setKey(Crypto.encryptRSA(getEncoder().encodeToString(encodedKey), this.getUserPublicKey(userId)))
                .setContent(Crypto.encryptAES(secretKeySpec, responseString))
                .setSignature(Crypto.sign(responseSignatureString, this.privateKey))
                .build();
    }

    public int getUserIdFromObtainLocationReportRequest(LocationServer.ObtainLocationReportRequest request) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(request.getKey(), this.privateKey);
            String requestContent = Crypto.decryptAES(secretKeySpec, request.getContent());
            LocationReportRequest locationRequest = objectMapper.readValue(requestContent, LocationReportRequest.class);
            return locationRequest.getSourceClientId();
        } catch (GeneralSecurityException | JsonProcessingException ex) {
            return 0;
        }
    }

    public int getUserIdFromRequestUserProofsRequest(LocationServer.RequestMyProofsRequest request) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(request.getKey(), this.privateKey);
            String requestContent = Crypto.decryptAES(secretKeySpec, request.getContent());
            ProofsRequest locationProofRequest = objectMapper.readValue(requestContent, ProofsRequest.class);
            return locationProofRequest.getUserId();
        } catch (GeneralSecurityException | JsonProcessingException ex) {
            return 0;
        }
    }

    public int getUserIdFromSubmitLocationReportRequest(LocationServer.SubmitLocationReportRequest request) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(request.getKey(), this.privateKey);
            String requestHeader = Crypto.decryptAES(secretKeySpec, request.getHeader());
            SubmitLocationReportRequestHeader locationReportRequestHeader = objectMapper.readValue(requestHeader, SubmitLocationReportRequestHeader.class);
            return locationReportRequestHeader.getClientId();
        } catch (GeneralSecurityException | JsonProcessingException ex) {
            return 0;
        }
    }

    public StatusRuntimeException buildException(Status.Code code, String description, int userId, boolean sumSeqNumber) {
        try {
            byte[] encodedKey = Crypto.generateSecretKey();
            SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");
            Status status = Status.fromCode(code).withDescription(description);

            if (sumSeqNumber) seqNumbers.put(userId, seqNumbers.get(userId) + 1);

            ExceptionMetadata exceptionMetadata = new ExceptionMetadata(Status.fromCode(code).getCode().toString(), description, userId, this.id, seqNumbers.get(userId));

            Metadata metadata = new Metadata();

            Metadata.Key<String> symmetricKey = Metadata.Key.of("symmetricKey", Metadata.ASCII_STRING_MARSHALLER);
            Metadata.Key<String> exceptionMetadataKey = Metadata.Key.of("exceptionMetadataKey", Metadata.ASCII_STRING_MARSHALLER);
            Metadata.Key<String> signature = Metadata.Key.of("signature", Metadata.ASCII_STRING_MARSHALLER);

            metadata.put(symmetricKey, Crypto.encryptRSA(getEncoder().encodeToString(encodedKey), this.getUserPublicKey(userId)));
            metadata.put(exceptionMetadataKey, Crypto.encryptAES(secretKeySpec, exceptionMetadata.toJsonString()));
            metadata.put(signature, Crypto.sign(exceptionMetadata.toJsonString() + secretKeySpec.toString(), this.privateKey));

            return status.asRuntimeException(metadata);
        } catch (GeneralSecurityException ex) {
            return Status.fromCode(code).withDescription(description).asRuntimeException();
        }
    }

    private synchronized void saveCurrentServerState() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode seqArrayNode = objectMapper.createArrayNode();
        ArrayNode arrayNode = objectMapper.createArrayNode();

        for (Pair<Integer, Integer> key: locationReports.keySet()) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("userId", key.getValue0());
            node.put("epoch", key.getValue1());
            node.put("content", locationReports.get(key).toJsonString());

            arrayNode.add(node);
        }

        for (Integer clientId : seqNumbers.keySet()) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("userId", clientId);
            node.put("seqNumber", seqNumbers.get(clientId));

            seqArrayNode.add(node);
        }

        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("numberOfUsers", numberOfUsers);
        node.put("step", step);
        node.put("maxByzantineUsers", maxByzantineUsers);
        node.put("maxNearbyByzantineUsers", maxNearbyByzantineUsers);
        node.put("maxReplicas", maxReplicas);
        node.put("maxByzantineReplicas", maxByzantineReplicas);
        node.set("locationReports", arrayNode);
        node.set("seqNumbers", seqArrayNode);

        objectMapper.writeValue(new File(SERVER_RECOVERY_FILE_PATH), node);
        objectMapper.writeValue(new File(BACKUP_RECOVERY_FILE_PATH), node);
    }

    public void cleanUp() {
        new File(SERVER_RECOVERY_FILE_PATH).delete();
        new File(BACKUP_RECOVERY_FILE_PATH).delete();

        locationReports = new ConcurrentHashMap<>();
        initializeBrb();
        initializeSeqNumbers();
    }
}
