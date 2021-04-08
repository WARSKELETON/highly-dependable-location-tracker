package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.StatusRuntimeException;
import pt.tecnico.hdlt.T25.Proximity;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ByzantineClient extends Client {

    public enum Flavor {
        IMPERSONATE,
        SILENT,
        SPOOFER,
        DIGEST,
        CONSPIRATOR
    }

    private Flavor flavor;

    public ByzantineClient(String serverHost, int serverPort, int clientId, SystemInfo systemInfo, int maxNearbyByzantineUsers, Flavor flavor, boolean isTest) throws IOException, GeneralSecurityException {
        super(serverHost, serverPort, clientId, systemInfo, maxNearbyByzantineUsers, isTest);
        this.flavor = flavor;
    }

    @Override
    public boolean createLocationReport(int ep, int latitude, int longitude) throws InterruptedException {
        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();
        Location location = new Location(this.getClientId(), ep, latitude, longitude);

        List<Integer> nearbyUsers = getNearbyUsers(getMyLocation(ep));

        final CountDownLatch finishLatch = new CountDownLatch(this.getMaxNearbyByzantineUsers());

        System.out.println("Nearby users: " + nearbyUsers.size());
        for (int witnessId : nearbyUsers) {
            System.out.println(String.format("Sending Location Proof Request to %s...", witnessId));

            LocationProof locationProof = new LocationProof(location.getUserId(), location.getEp(), location.getLatitude(), location.getLongitude(), witnessId);

            Consumer<Proximity.LocationProofResponse> requestObserver = new Consumer<>() {
                @Override
                public void accept(Proximity.LocationProofResponse response) {
                    if (Crypto.verify(response.getContent(), response.getSignature(), getUserPublicKey(witnessId)) && verifyLocationProofResponse(locationProof, response.getContent())) {
                        locationProofsContent.put(witnessId, response.getContent());
                        locationProofsSignatures.put(witnessId, response.getSignature());
                        System.out.println(String.format("Received legitimate proof from %s...", witnessId));
                        finishLatch.countDown();
                    } else {
                        System.out.println(String.format("Received illegitimate proof from %s...", witnessId));
                    }
                }
            };

            this.requestLocationProof(locationProof, witnessId, requestObserver);
        }

        finishLatch.await(20, TimeUnit.SECONDS);

        long count = finishLatch.getCount();
        System.out.println("Count " + count);

        // If necessary build own fake locations proof to complete quorum
        while (count > 0) {
            LocationProof locationProof = new LocationProof(this.getClientId(), ep, latitude, longitude, this.getClientId());
            locationProofsContent.put(this.getClientId(), locationProof.toJsonString());
            locationProofsSignatures.put(this.getClientId(), Crypto.sign(locationProof.toJsonString(), this.getPrivateKey()));

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

    // Byzantine user fucking with the signature
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
    void parseCommand(String cmd) throws GeneralSecurityException, JsonProcessingException {
        String[] args = cmd.split(" ");

        if (args.length < 2) {
            return;
        }

        if (args[0].equals(LOCATION_PROOF_REQUEST)) {
            int ep = Integer.parseInt(args[1]);
            int latitude = Integer.parseInt(args[2]);
            int longitude = Integer.parseInt(args[3]);
            try {
                createLocationReport(ep, latitude, longitude);
            } catch (InterruptedException e) {
                System.err.println("Caught Interrupted exception");
            }
        }

        else if (args[0].equals(SUBMIT_LOCATION_REPORT)) {
            int ep = Integer.parseInt(args[1]);
            try {
                submitLocationReport(ep);
            } catch (InterruptedException ex) {
                System.err.println("Caught Interrupted exception");
            } catch (StatusRuntimeException ex2) {
                System.err.println(ex2.getMessage());
            }
        }

        else if (args[0].equals(OBTAIN_LOCATION_REPORT)) {
            int ep = Integer.parseInt(args[1]);
            try {
                obtainLocationReport(this.getClientId(), ep);
            } catch (StatusRuntimeException ex2) {
                System.err.println(ex2.getMessage());
            }
        }

        else
            System.out.println("Type invalid. Possible types are car and person.");
    }
}
