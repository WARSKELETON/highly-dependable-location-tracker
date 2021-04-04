package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import pt.tecnico.hdlt.T25.Proximity;
import pt.tecnico.hdlt.T25.ProximityServiceGrpc;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ByzantineClient extends Client {
    public ByzantineClient(String serverHost, int serverPort, int clientId, SystemInfo systemInfo) throws IOException {
        super(serverHost, serverPort, clientId, systemInfo);
    }

    @Override
    protected void requestLocationProof(int ep) throws JsonProcessingException {
        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();
        Location location = this.getSystemInfo().getGrid().stream()
                .filter(location1 -> location1.getEp() == ep && location1.getUserId() == this.getClientId())
                .collect(Collectors.toList())
                .get(0);

        List<Integer> nearbyUsers = getNearbyUsers(location);
        System.out.println("Nearby users: " + nearbyUsers.size());
        for (int witnessId : nearbyUsers) {
            System.out.println(String.format("Sending Location Proof Request to %s...", witnessId));

            LocationProof locationProof = new LocationProof(location.getUserId(), location.getEp(), location.getLatitude(), location.getLongitude(), witnessId, 0, 0);
            String content = locationProof.toJsonString();

            Proximity.LocationProofRequest request = Proximity.LocationProofRequest.newBuilder()
                    .setContent(content)
                    .setSignature(Crypto.sign(content, this.getPrivateKey()))
                    .build();

            Proximity.LocationProofResponse response = proximityServiceStubs.get(witnessId).requestLocationProof(request);

            // Byzantine user tries to create a false proof of its own location
            if (Crypto.verify(response.getContent(), response.getSignature(), this.getUserPublicKey(witnessId))) {
                locationProofsContent.put(witnessId, response.getContent());
                locationProofsSignatures.put(witnessId, response.getSignature());
                System.out.println(String.format("Received Proof from %s...", witnessId));
            } else {
                System.out.println(String.format("Illegitimate Proof from %s...", witnessId));
            }
        }

        String signature = location.toJsonString() + locationProofsSignatures.values().stream().reduce("", String::concat);
        LocationReport locationReport = new LocationReport(location, Crypto.sign(signature, this.getPrivateKey()), locationProofsContent, locationProofsSignatures);
        locationReports.put(ep, locationReport);
    }

    // Byzantine user responds (falsely) to a proof of location request on behalf of the impersonated user
    @Override
    public boolean verifyLocationProofRequest(Proximity.LocationProofRequest request) throws IOException {
        boolean success = true;
        if (Math.random() < 0.25)
            success = false;
        return super.verifyLocationProofRequest(request) || success;
    }

}