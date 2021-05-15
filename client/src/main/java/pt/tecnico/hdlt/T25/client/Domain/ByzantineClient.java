package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.LocationServerServiceGrpc;
import pt.tecnico.hdlt.T25.Proximity;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.grpc.Status.DEADLINE_EXCEEDED;

public class ByzantineClient extends Client {

    public enum Flavor {
        IMPERSONATE,
        SILENT,
        SPOOFER,
        DIGEST,
        CONSPIRATOR,
        STUBBORN
    }

    private Flavor flavor;

    public ByzantineClient(String serverHost, int serverPort, int clientId, SystemInfo systemInfo, int maxByzantineUsers, int maxNearbyByzantineUsers, Flavor flavor, boolean isTest, int maxReplicas, int maxByzantineReplicas) throws IOException, GeneralSecurityException, InterruptedException {
        super(serverHost, serverPort, clientId, systemInfo, maxByzantineUsers, maxNearbyByzantineUsers, isTest, maxReplicas, maxByzantineReplicas);
        this.flavor = flavor;
    }

    public Flavor getFlavor() {
        return flavor;
    }

    public void setFlavor(Flavor flavor) {
        this.flavor = flavor;
    }

    public LocationServer.ObtainUsersAtLocationRequest buildObtainUsersAtLocationRequest(int latitude, int longitude, int ep, int currentSeqNumber, int serverId) throws GeneralSecurityException {
        Location usersLocationRequest = new Location(-1, ep, latitude, longitude);
        String requestContent = usersLocationRequest.toJsonString();
        String signatureString = requestContent + currentSeqNumber;

        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        return LocationServer.ObtainUsersAtLocationRequest.newBuilder()
                .setKey(Crypto.encryptRSA(Base64.getEncoder().encodeToString(encodedKey), this.getServerPublicKey(serverId)))
                .setContent(Crypto.encryptAES(secretKeySpec, requestContent))
                .setSignature(Crypto.sign(signatureString, this.getPrivateKey()))
                .setSeqNumber(Crypto.encryptAES(secretKeySpec, String.valueOf(currentSeqNumber)))
                .build();
    }

    private boolean verifyUsersReportResponse(LocationServer.ObtainLocationReportResponse response, Location location, int latitude, int longitude, int ep) throws GeneralSecurityException, JsonProcessingException {
        return verifyLocationReport(location.getUserId(), ep, response) && location.getEp() == ep && location.getLatitude() == latitude && location.getLongitude() == longitude;
    }

    private boolean verifyObtainUsersAtLocationResponse(LocationServer.ObtainUsersAtLocationResponse response) throws GeneralSecurityException {
        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), getPrivateKey());
        String serverId = Crypto.decryptAES(secretKeySpec, response.getServerId());
        String seqNumber = Crypto.decryptAES(secretKeySpec, response.getSeqNumber());
        List<String> reportSignatures = new ArrayList<>();

        for (LocationServer.ObtainLocationReportResponse report : response.getLocationReportsList()) {
            reportSignatures.add(report.getServerSignature());
        }

        String serverSignature = reportSignatures.stream().reduce("", String::concat) + secretKeySpec.toString() + seqNumber + serverId;
        return Crypto.verify(serverSignature, response.getServerSignature(), this.getServerPublicKey(Integer.parseInt(serverId)));
    }

    public List<Location> obtainUsersAtLocationRegular(int latitude, int longitude, int ep) throws GeneralSecurityException, InterruptedException {
        Map<Integer, Location> locations = new ConcurrentHashMap<>();

        final CountDownLatch finishLatch = new CountDownLatch((getMaxReplicas() + getMaxByzantineReplicas()) / 2 + 1);

        Consumer<LocationServer.ObtainUsersAtLocationResponse> requestObserver = new Consumer<>() {
            @Override
            public void accept(LocationServer.ObtainUsersAtLocationResponse response)  {
                synchronized (finishLatch) {
                    if (finishLatch.getCount() == 0) return;

                    try {
                        if (verifyObtainUsersAtLocationResponse(response)) {
                            ObjectMapper objectMapper = new ObjectMapper();
                            SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), getPrivateKey());

                            for (LocationServer.ObtainLocationReportResponse report : response.getLocationReportsList()) {
                                String locationProverContent = Crypto.decryptAES(secretKeySpec, report.getLocationProver().getContent());
                                Location location = objectMapper.readValue(locationProverContent, Location.class);

                                if (verifyUsersReportResponse(report, location, latitude, longitude, ep)) {
                                    locations.put(location.getUserId(), location);
                                }
                            }
                            int serverId = Integer.parseInt(Crypto.decryptAES(secretKeySpec, response.getServerId()));
                            finishLatch.countDown();
                        }
                    } catch (JsonProcessingException ex) {
                        System.err.println("user" + getClientId() + ": caught processing exception while obtaining users at location");
                    } catch (GeneralSecurityException ex2) {
                        System.err.println("user" + getClientId() + ": caught security exception while obtaining users at location");
                    }
                }
            }
        };

        for (int serverId : getLocationServerServiceStubs().keySet()) {
            LocationServer.ObtainUsersAtLocationRequest request = buildObtainUsersAtLocationRequest(latitude, longitude, ep, this.getSeqNumbers().get(serverId), serverId);
            obtainUsersAtLocation(getLocationServerServiceStubs().get(serverId), request, requestObserver);
            getSeqNumbers().put(serverId, getSeqNumbers().get(serverId) + 1);
        }

        finishLatch.await(5, TimeUnit.SECONDS);

        if (finishLatch.getCount() == 0) {
            System.out.println("user" + getClientId() + ": Got these users");
            for (Location location : locations.values()) {
                System.out.println("user" + getClientId() + ": Report from user" + location.getUserId() + " ep " + location.getEp() + " coords at " + location.getLatitude() + ", " + location.getLongitude());
            }
        } else {
            obtainUsersAtLocationRegular(latitude, longitude, ep);
        }

        return new ArrayList<>(locations.values());
    }

    public void obtainUsersAtLocation(LocationServerServiceGrpc.LocationServerServiceStub locationServerServiceStub, LocationServer.ObtainUsersAtLocationRequest request, Consumer<LocationServer.ObtainUsersAtLocationResponse> callback) {
        try {
            locationServerServiceStub.withDeadlineAfter(1, TimeUnit.SECONDS).obtainUsersAtLocation(request, new StreamObserver<>() {
                @Override
                public void onNext(LocationServer.ObtainUsersAtLocationResponse response) {
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

    @Override
    public boolean createLocationReport(int clientId, int ep, int latitude, int longitude) throws InterruptedException {
        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();
        Location location = new Location(clientId, ep, latitude, longitude);

        final CountDownLatch finishLatch = new CountDownLatch(this.getMaxByzantineUsers());

        System.out.println("Byzantine user" + getClientId() + ": broadcasting location proof request!");
        for (int witnessId : getProximityServiceStubs().keySet()) {
            System.out.println("Byzantine user" + getClientId() + ": Sending Location Proof Request to user" + witnessId + "...");

            LocationProof locationProof = new LocationProof(location.getUserId(), location.getEp(), location.getLatitude(), location.getLongitude(), witnessId);

            Consumer<Proximity.LocationProofResponse> requestObserver = new Consumer<>() {
                @Override
                public void accept(Proximity.LocationProofResponse response) {
                    locationProofsContent.put(witnessId, response.getContent());
                    locationProofsSignatures.put(witnessId, response.getSignature());
                    System.out.println("Byzantine user" + getClientId() + ": Received proof from user" + witnessId);
                    finishLatch.countDown();
                }
            };

            this.requestLocationProof(locationProof, witnessId, requestObserver);
        }

        finishLatch.await(10, TimeUnit.SECONDS);

        long count = finishLatch.getCount();

        // If necessary build own fake locations proof to complete quorum
        while (count > 0) {
            int victimClientId = new Random().nextInt(getSystemInfo().getNumberOfUsers());
            System.out.println("Byzantine user" + getClientId() + ": building fake proof of my location complete report of " + getMaxByzantineUsers() + " proofs");
            LocationProof locationProof = new LocationProof(clientId, ep, latitude, longitude, victimClientId);
            locationProofsContent.put(victimClientId, locationProof.toJsonString());
            locationProofsSignatures.put(victimClientId, Crypto.sign(locationProof.toJsonString(), this.getPrivateKey()));

            count--;
        }

        String signature = location.toJsonString() + locationProofsContent.values().stream().reduce("", String::concat);
        LocationReport locationReport = new LocationReport(location, Crypto.sign(signature, this.getPrivateKey()), locationProofsContent, locationProofsSignatures);
        getLocationReports().put(ep, locationReport);
        return true;
    }

    @Override
    public boolean verifyLocationProofRequest(Proximity.LocationProofRequest request) {
        return true;
    }

    @Override
    public Proximity.LocationProofResponse buildLocationProof(Proximity.LocationProofRequest request) throws JsonProcessingException {
        switch (flavor) {
            case IMPERSONATE:
                return buildFalseLocationProofImpersonate(request);
            case SILENT:
                return buildLocationProofNoResponse(request);
            case SPOOFER:
                return buildFalseLocationProofCoords(request);
            case DIGEST:
                return buildFalseLocationProofSignature(request);
            case CONSPIRATOR:
                return buildLegitimateLocationProof(request);
        }
        return buildLegitimateLocationProof(request);
    }

    // Since already "verified" it can sign and send. Byzantine behaviour at verifyLocationProofRequest
    private Proximity.LocationProofResponse buildLegitimateLocationProof(Proximity.LocationProofRequest request) throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        LocationProof locationProof = objectMapper.readValue(request.getContent(), LocationProof.class);

        String content = locationProof.toJsonString();

        return Proximity.LocationProofResponse
                .newBuilder()
                .setContent(content)
                .setSignature(Crypto.sign(content, this.getPrivateKey()))
                .build();
    }

    // Byzantine user sleeps for 70 seconds, not sending a responds
    private Proximity.LocationProofResponse buildLocationProofNoResponse(Proximity.LocationProofRequest request) throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        LocationProof locationProof = objectMapper.readValue(request.getContent(), LocationProof.class);

        String content = locationProof.toJsonString();

        try {
            Thread.sleep(70000);
        } catch (InterruptedException e) {
            System.out.println("Error when sleeping.");
        }

        return Proximity.LocationProofResponse
                .newBuilder()
                .setContent(content)
                .setSignature(Crypto.sign(content, this.getPrivateKey()))
                .build();
    }

    // Byzantine user responds impersonating as another witness
    private Proximity.LocationProofResponse buildFalseLocationProofImpersonate(Proximity.LocationProofRequest request) throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        LocationProof locationProof = objectMapper.readValue(request.getContent(), LocationProof.class);

        List<Integer> nearbyUsers = getNearbyUsers(getMyLocation(locationProof.getEp()));

        locationProof.setWitnessId(nearbyUsers.get(new Random().nextInt(nearbyUsers.size())));

        String content = locationProof.toJsonString();

        return Proximity.LocationProofResponse
                .newBuilder()
                .setContent(content)
                .setSignature(Crypto.sign(content, this.getPrivateKey()))
                .build();
    }

    // Byzantine user responds spoofing the prover's location
    private Proximity.LocationProofResponse buildFalseLocationProofCoords(Proximity.LocationProofRequest request) throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        LocationProof locationProof = objectMapper.readValue(request.getContent(), LocationProof.class);

        List<Location> grid = getSystemInfo().getGrid();
        Location randomLocation = grid.get(new Random().nextInt(grid.size()));

        locationProof.setLatitude(randomLocation.getLatitude());
        locationProof.setLongitude(randomLocation.getLongitude());

        String content = locationProof.toJsonString();

        return Proximity.LocationProofResponse
                .newBuilder()
                .setContent(content)
                .setSignature(Crypto.sign(content, this.getPrivateKey()))
                .build();
    }

    // Byzantine user alters proof's signature
    private Proximity.LocationProofResponse buildFalseLocationProofSignature(Proximity.LocationProofRequest request) throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        LocationProof locationProof = objectMapper.readValue(request.getContent(), LocationProof.class);
        LocationProof fakeLocationProof = objectMapper.readValue(request.getContent(), LocationProof.class);
        fakeLocationProof.setEp(locationProof.getEp() + 1);

        String content = locationProof.toJsonString();
        String fakeContent = fakeLocationProof.toJsonString();

        return Proximity.LocationProofResponse
                .newBuilder()
                .setContent(content)
                .setSignature(Crypto.sign(fakeContent, this.getPrivateKey()))
                .build();
    }

    @Override
    public void submitLocationReportAtomic(int ep) throws InterruptedException, GeneralSecurityException {
        final CountDownLatch finishLatch = new CountDownLatch(getMaxByzantineReplicas());

        Consumer<LocationServer.SubmitLocationReportResponse> requestOnSuccessObserver = new Consumer<>() {
            @Override
            public void accept(LocationServer.SubmitLocationReportResponse response)  {
                synchronized (finishLatch) {
                    if (finishLatch.getCount() == 0) return;

                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), getPrivateKey());
                        String responseString = Crypto.decryptAES(secretKeySpec, response.getContent());
                        SubmitLocationReportResponse submitResponse = objectMapper.readValue(responseString, SubmitLocationReportResponse.class);
                        String signatureString = responseString + secretKeySpec.toString();

                        if (Crypto.verify(signatureString, response.getSignature(), getServerPublicKey(submitResponse.getServerId())) && submitResponse.isOk()) {
                            System.out.println("user" + getClientId() + ": Received submit location report response -> " + submitResponse.isOk());
                            finishLatch.countDown();
                        } else if (Crypto.verify(signatureString, response.getSignature(), getServerPublicKey(submitResponse.getServerId())) && !submitResponse.isOk()) {
                            System.out.println("user" + getClientId() + ": Received submit location report not ok response -> " + submitResponse.isOk());
                        }
                    } catch (JsonProcessingException|GeneralSecurityException ex) {
                        System.err.println("user" + getClientId() + ": caught processing exception while submitting report");
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
                        System.out.println("user" + getClientId() + ": Duplicate report!");
                        finishLatch.countDown();
                    }
                }
            }
        };

        for (int serverId : getLocationServerServiceStubs().keySet()) {
            LocationServer.SubmitLocationReportRequest request = buildSubmitLocationReportRequest(ep, serverId);
            submitLocationReport(getLocationServerServiceStubs().get(serverId), request, requestOnSuccessObserver, requestOnErrorObserver);
        }

        finishLatch.await(10, TimeUnit.SECONDS);

        if (finishLatch.getCount() == 0) {
            System.out.println("user" + getClientId() + ": Finished byzantine submission!");
        } else {
            System.out.println("user" + getClientId() + ": Retrying byzantine submit report!");
            submitLocationReportAtomic(ep);
        }
    }

    @Override
    public Location obtainLocationReportAtomic(int userId, int ep) throws GeneralSecurityException, InterruptedException, JsonProcessingException {
        List<LocationServer.ObtainLocationReportResponse> reportResponses = new ArrayList<>();

        final CountDownLatch finishLatch = new CountDownLatch((getMaxReplicas() + getMaxByzantineReplicas()) / 2 + 1);

        Consumer<LocationServer.ObtainLocationReportResponse> requestOnSuccessObserver = new Consumer<>() {
            @Override
            public void accept(LocationServer.ObtainLocationReportResponse response) {
                synchronized (finishLatch) {
                    if (finishLatch.getCount() == 0) return;

                    try {
                        if (flavor != Flavor.STUBBORN && response != null && verifyLocationReport(userId, ep, response)) {
                            ObjectMapper objectMapper = new ObjectMapper();
                            SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(response.getKey(), getPrivateKey());
                            int serverId = Integer.parseInt(Crypto.decryptAES(secretKeySpec, response.getServerId()));
                            String locationProverContent = Crypto.decryptAES(secretKeySpec, response.getLocationProver().getContent());
                            Location locationProver = objectMapper.readValue(locationProverContent, Location.class);

                            reportResponses.add(response);
                            while (finishLatch.getCount() > 0) {
                                finishLatch.countDown();
                            }
                        } else if (flavor == Flavor.STUBBORN) {
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

                    if (throwable.getMessage().contains("NOT_FOUND") && flavor != Flavor.STUBBORN) {
                        finishLatch.countDown();
                    }
                    System.out.println(throwable.getMessage());
                }
            }
        };

        for (int serverId : getLocationServerServiceStubs().keySet()) {
            LocationServer.ObtainLocationReportRequest request = this.buildObtainLocationReportRequest(userId, ep, this.getSeqNumbers().get(serverId), serverId);
            obtainLocationReport(getLocationServerServiceStubs().get(serverId), request, requestOnSuccessObserver, requestOnErrorObserver);
            this.getSeqNumbers().put(serverId, this.getSeqNumbers().get(serverId) + 1);
        }

        finishLatch.await(5, TimeUnit.SECONDS);

        if (finishLatch.getCount() == 0 && !reportResponses.isEmpty()) {
            return obtainLocationFromReportResponse(reportResponses.get(0));
        } else {
            if (finishLatch.getCount() == 0 && reportResponses.isEmpty()) {
                return null;
            } else {
                obtainLocationReportAtomic(userId, ep);
            }
        }

        return obtainLocationFromReportResponse(reportResponses.get(0));
    }

    @Override
    void parseCommand(String cmd) throws JsonProcessingException {
        String[] args = cmd.split(" ");

        if (args.length < 2) {
            return;
        }

        try {
            switch (args[0]) {
                case LOCATION_PROOF_REQUEST: {
                    int ep = Integer.parseInt(args[1]);
                    int latitude = Integer.parseInt(args[2]);
                    int longitude = Integer.parseInt(args[3]);
                    createLocationReport(getClientId(), ep, latitude, longitude);
                    break;
                }
                case SUBMIT_LOCATION_REPORT: {
                    int ep = Integer.parseInt(args[1]);
                    submitLocationReportAtomic(ep);
                    break;
                }
                case OBTAIN_LOCATION_REPORT: {
                    int ep = Integer.parseInt(args[1]);
                    obtainLocationReportAtomic(this.getClientId(), ep);
                    break;
                }
                default:
                    System.out.println("Invalid operation or invalid number of arguments. Possible operations are proof, submit and obtain.");
                    break;
            }
        } catch (InterruptedException ex) {
            System.err.println("Caught Interrupted exception");
        } catch (StatusRuntimeException ex) {
            System.err.println(ex.getMessage());
        } catch (GeneralSecurityException ex) {
            System.err.println("Caught No Such Algorithm exception");
        }
    }
}
