package pt.tecnico.hdlt.T25.server.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.javatuples.Pair;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.crypto.Crypto;
import pt.tecnico.hdlt.T25.server.Domain.Exceptions.DuplicateReportException;
import pt.tecnico.hdlt.T25.server.Domain.Exceptions.InvalidNumberOfProofsException;
import pt.tecnico.hdlt.T25.server.Domain.Exceptions.InvalidProofOfWorkException;
import pt.tecnico.hdlt.T25.server.Domain.Exceptions.InvalidSignatureException;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;

public class ByzantineServer extends Server {
    public enum Flavor {
        BRBFOLLOWER,
        UNVERIFIED
    }

    private Flavor flavor;

    public ByzantineServer(int serverId, int numberOfUsers, int step, int maxByzantineUsers, int maxNearbyByzantineUsers, int maxReplicas, int maxByzantineReplicas, boolean isTest, String keystorePassword) throws IOException, GeneralSecurityException, InterruptedException {
        super(serverId, numberOfUsers, step, maxByzantineUsers, maxNearbyByzantineUsers, maxReplicas, maxByzantineReplicas, isTest, keystorePassword);
        this.flavor = Flavor.BRBFOLLOWER;
    }

    public Flavor getFlavor() {
        return flavor;
    }

    public void setFlavor(Flavor flavor) {
        this.flavor = flavor;
    }

    @Override
    public LocationServer.SubmitLocationReportResponse submitLocationReport(LocationServer.SubmitLocationReportRequest report) throws IOException, GeneralSecurityException, DuplicateReportException, InvalidSignatureException, InvalidNumberOfProofsException, InterruptedException, InvalidProofOfWorkException {
        ObjectMapper objectMapper = new ObjectMapper();

        SecretKeySpec secretKeySpec = Crypto.decryptKeyWithRSA(report.getKey(), this.getPrivateKey());
        String locationProverContent = Crypto.decryptAES(secretKeySpec, report.getLocationProver().getContent());

        Location locationProver = objectMapper.readValue(locationProverContent, Location.class);

        Map<Integer, String> locationProofsContent = new HashMap<>();
        Map<Integer, String> locationProofsSignatures = new HashMap<>();

        System.out.println("Server" + this.getId() + ": Initiating verification of report with location: User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());

        for (LocationServer.LocationMessage locationProof : report.getLocationProofsList()) {
            String locationProofContent = Crypto.decryptAES(secretKeySpec, locationProof.getContent());

            LocationProof proof = objectMapper.readValue(locationProofContent, LocationProof.class);
            int witnessId = proof.getWitnessId();

            locationProofsContent.put(witnessId, locationProofContent);
            locationProofsSignatures.put(witnessId, locationProof.getSignature());
        }

        LocationReport locationReport = new LocationReport(locationProver, report.getLocationProver().getSignature(), locationProofsContent, locationProofsSignatures);

        if (this.flavor == Flavor.BRBFOLLOWER && !broadcastReport(locationReport)) {
            cleanBrb(locationProver.getUserId());
            System.out.println("Server" + this.getId() + ": Failed to submit report! Location: User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
            return buildSubmitLocationReportResponse(false, locationProver.getUserId());
        }
        deliverReport(locationReport);

        System.out.println("Server" + this.getId() + ": Report submitted with success! Location: User" + locationProver.getUserId() + " at " + locationProver.getEp() + " " + locationProver.getLatitude() +  ", " + locationProver.getLongitude());
        return buildSubmitLocationReportResponse(true, locationProver.getUserId());
    }

    @Override
    public void cleanUp() {
        super.cleanUp();
        this.flavor = Flavor.BRBFOLLOWER;
    }
}
