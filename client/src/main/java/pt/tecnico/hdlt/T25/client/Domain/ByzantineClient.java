package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.StatusRuntimeException;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.Proximity;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.grpc.Status.DEADLINE_EXCEEDED;

public class ByzantineClient extends Client {

    public enum Flavor {
        IMPERSONATE,
        SILENT,
        SPOOFER,
        DIGEST,
        CONSPIRATOR
    }

    private Flavor flavor;

    public ByzantineClient(String serverHost, int serverPort, int clientId, SystemInfo systemInfo, int maxByzantineUsers, int maxNearbyByzantineUsers, Flavor flavor, boolean isTest) throws IOException, GeneralSecurityException {
        super(serverHost, serverPort, clientId, systemInfo, maxByzantineUsers, maxNearbyByzantineUsers, isTest);
        this.flavor = flavor;
    }

    public Flavor getFlavor() {
        return flavor;
    }

    public void setFlavor(Flavor flavor) {
        this.flavor = flavor;
    }

    public void obtainUsersAtLocation(int latitude, int longitude, int ep) throws JsonProcessingException, GeneralSecurityException {
        int currentSeqNumber;
        synchronized (getSeqNumberLock()) {
            currentSeqNumber = this.getSeqNumber();
            setSeqNumber(currentSeqNumber + 1);
        }

        Location usersLocationRequest = new Location(-1, ep, latitude, longitude);
        String requestContent = usersLocationRequest.toJsonString();
        String signatureString = requestContent + currentSeqNumber;

        byte[] encodedKey = Crypto.generateSecretKey();
        SecretKeySpec secretKeySpec = new SecretKeySpec(encodedKey, "AES");

        LocationServer.ObtainUsersAtLocationRequest request = LocationServer.ObtainUsersAtLocationRequest.newBuilder()
                .setKey(Crypto.encryptRSA(Base64.getEncoder().encodeToString(encodedKey), this.getServerPublicKey()))
                .setContent(Crypto.encryptAES(secretKeySpec, requestContent))
                .setSignature(Crypto.sign(signatureString, this.getPrivateKey()))
                .setSeqNumber(Crypto.encryptAES(secretKeySpec, String.valueOf(currentSeqNumber)))
                .build();

        List<LocationServer.ObtainLocationReportResponse> reports;
        while (true) {
            try {
                reports = this.getLocationServerServiceStub().withDeadlineAfter(1, TimeUnit.SECONDS).obtainUsersAtLocation(request).getLocationReportsList();
                break;
            } catch (StatusRuntimeException e) {
                if (e.getStatus().getCode().equals(DEADLINE_EXCEEDED.getCode())) {
                    System.out.println("user" + getClientId() + ": TIMEOUT CLIENT");
                } else {
                    throw e;
                }
            }
        }

        for (LocationServer.ObtainLocationReportResponse report : reports) {
            verifyLocationReport(report, currentSeqNumber);
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
                    submitLocationReport(ep);
                    break;
                }
                case OBTAIN_LOCATION_REPORT: {
                    int ep = Integer.parseInt(args[1]);
                    obtainLocationReport(this.getClientId(), ep);
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
