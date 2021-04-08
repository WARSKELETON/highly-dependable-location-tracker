package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.crypto.Crypto;

import java.util.List;

import static pt.tecnico.hdlt.T25.crypto.Crypto.getPriv;

public class HAClient extends AbstractClient {
    private static final String OBTAIN_LOCATION_REPORT = "obtainLocation";
    private static final String OBTAIN_USERS_AT_LOCATION = "obtainUsers";

    public HAClient(String serverHost, int serverPort, int clientId, SystemInfo systemInfo, boolean isTest) {
        super(serverHost, serverPort, clientId, systemInfo);
        this.setPrivateKey(getPriv("ha-priv.key"));
        if (!isTest) this.eventLoop();
    }

    private void obtainUsersAtLocation(int latitude, int longitude, int ep) throws JsonProcessingException {
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
    void parseCommand(String cmd) {
        String[] args = cmd.split(" ");

        try {
            if (args[0].equals(OBTAIN_LOCATION_REPORT) && args.length == 3) {
                int userId = Integer.parseInt(args[1]);
                int ep = Integer.parseInt(args[2]);
                this.obtainLocationReport(userId, ep);
            } else if (args[0].equals(OBTAIN_USERS_AT_LOCATION) && args.length == 4) {
                int latitude = Integer.parseInt(args[1]);
                int longitude = Integer.parseInt(args[2]);
                int ep = Integer.parseInt(args[3]);
                this.obtainUsersAtLocation(latitude, longitude, ep);
            } else
                System.out.println("Invalid operation or invalid number of arguments. Possible operations are obtainLocation and obtainUsers.");
        } catch (JsonProcessingException ex) {
            System.err.println("Caught JSON Processing exception");
        }
    }
}
