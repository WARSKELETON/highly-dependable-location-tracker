package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.grpc.StatusRuntimeException;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import java.security.GeneralSecurityException;
import java.util.List;

import static pt.tecnico.hdlt.T25.crypto.Crypto.getPriv;

public class HAClient extends AbstractClient {
    private static final String OBTAIN_LOCATION_REPORT = "obtainLocation";
    private static final String OBTAIN_USERS_AT_LOCATION = "obtainUsers";

    public HAClient(String serverHost, int serverPort, int clientId, SystemInfo systemInfo, boolean isTest) throws GeneralSecurityException {
        super(serverHost, serverPort, clientId, systemInfo);
        this.setPrivateKey(getPriv("ha-priv.key"));
        if (!isTest) this.eventLoop();
    }

    private void obtainUsersAtLocation(int latitude, int longitude, int ep) throws JsonProcessingException, GeneralSecurityException {
        Location usersLocationRequest = new Location(-1, ep, latitude, longitude);
        String requestContent = usersLocationRequest.toJsonString();

        LocationServer.ObtainUsersAtLocationRequest request = LocationServer.ObtainUsersAtLocationRequest.newBuilder()
                .setContent(Crypto.encryptRSA(requestContent, this.getServerPublicKey()))
                .setSignature(Crypto.sign(requestContent, this.getPrivateKey()))
                .build();

        List<LocationServer.ObtainLocationReportResponse> reports = this.getLocationServerServiceStub().obtainUsersAtLocation(request).getLocationReportsList();

        for (LocationServer.ObtainLocationReportResponse report : reports) {
            verifyLocationReport(report);
        }
    }

    @Override
    void parseCommand(String cmd) throws GeneralSecurityException, JsonProcessingException {

        String[] args = cmd.split(" ");

        if (args[0].equals(OBTAIN_LOCATION_REPORT)) {
            if (args.length != 3) {
                return;
            }
            int userId = Integer.parseInt(args[1]);
            int ep = Integer.parseInt(args[2]);

            try {
                this.obtainLocationReport(userId, ep);
            } catch (StatusRuntimeException ex2) {
                System.err.println(ex2.getMessage());
            }
        }

        else if (args[0].equals(OBTAIN_USERS_AT_LOCATION)) {
            if (args.length != 4) {
                return;
            }
            int latitude = Integer.parseInt(args[1]);
            int longitude = Integer.parseInt(args[2]);
            int ep = Integer.parseInt(args[3]);

            try {
                this.obtainUsersAtLocation(latitude, longitude, ep);
            } catch (StatusRuntimeException ex2) {
                System.err.println(ex2.getMessage());
            }
        }
    }
}
