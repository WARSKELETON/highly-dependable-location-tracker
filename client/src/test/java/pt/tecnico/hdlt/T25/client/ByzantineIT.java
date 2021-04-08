package pt.tecnico.hdlt.T25.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.*;
import pt.tecnico.hdlt.T25.client.Domain.ByzantineClient;
import pt.tecnico.hdlt.T25.client.Domain.Client;
import pt.tecnico.hdlt.T25.client.Domain.Location;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ByzantineIT extends TestBase {
    // initialization and clean-up for each test

    @BeforeEach
    public void setUp() {

    }

    @AfterEach
    public void tearDown() {
        server.cleanUp();
    }

    // tests

    @Test
    public void CorrectReportWithSufficientNeighbors() throws GeneralSecurityException, InterruptedException, JsonProcessingException {
        Client testClient = null;
        for (Client client : clients) {
            if (client.getNearbyUsers(client.getMyLocation(0)).size() >= client.getMaxNearbyByzantineUsers() * 2) {
                testClient = client;
                break;
            }
        }

        assert testClient != null;
        Location originalLocation = testClient.getMyLocation(0);

        testClient.submitLocationReport(0);

        Location locationResponse = testClient.obtainLocationReport(testClient.getClientId(), 0);
        Assertions.assertEquals(originalLocation.getUserId(), locationResponse.getUserId());
        Assertions.assertEquals(originalLocation.getEp(), locationResponse.getEp());
        Assertions.assertEquals(originalLocation.getLatitude(), locationResponse.getLatitude());
        Assertions.assertEquals(originalLocation.getLongitude(), locationResponse.getLongitude());
    }

    @Test
    public void ByzantineBuildsFakeReport() throws InterruptedException{
        ByzantineClient byzantineClient = byzantineClients.get(new Random().nextInt(byzantineClients.size()));

        assert byzantineClient != null;
        Location spoofedLocation = new Location(byzantineClient.getClientId(), 0, 0, 0);

        int gridIndex = new Random().nextInt(systemInfo.getGrid().size());
        spoofedLocation.setLatitude(systemInfo.getGrid().get(gridIndex).getLatitude());
        spoofedLocation.setLongitude(systemInfo.getGrid().get(gridIndex).getLongitude());

        byzantineClient.createLocationReport(0, spoofedLocation.getLatitude(), spoofedLocation.getLongitude());

        Assertions.assertEquals(Status.Code.INVALID_ARGUMENT,
                assertThrows(StatusRuntimeException.class, () -> byzantineClient.submitLocationReport(0)).getStatus().getCode());

    }
}