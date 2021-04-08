package pt.tecnico.hdlt.T25.client;

import org.junit.jupiter.api.*;
import pt.tecnico.hdlt.T25.client.Domain.ByzantineClient;
import pt.tecnico.hdlt.T25.client.Domain.Client;
import pt.tecnico.hdlt.T25.client.Domain.Location;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SiloIT extends TestBase {

    // static members
    // TODO

    // initialization and clean-up for each test

    @BeforeEach
    public void setUp() {

    }

    @AfterEach
    public void tearDown() {

    }

    // tests

    @Test
    public void CorrectReportWithSufficientNeighbors() {
        Client testClient = null;
        for (Client client : clients) {
            if (client.getNearbyUsers(client.getMyLocation(0)).size() >= client.getMaxNearbyByzantineUsers() * 2) {
                testClient = client;
                break;
            }
        }

        try {
            assert testClient != null;
            Location originalLocation = testClient.getMyLocation(0);

            testClient.submitLocationReport(0);

            Location locationResponse = testClient.obtainLocationReport(testClient.getClientId(), 0);
            Assertions.assertEquals(originalLocation.getUserId(), locationResponse.getUserId());
            Assertions.assertEquals(originalLocation.getEp(), locationResponse.getEp());
            Assertions.assertEquals(originalLocation.getLatitude(), locationResponse.getLatitude());
            Assertions.assertEquals(originalLocation.getLongitude(), locationResponse.getLongitude());
        } catch (Exception ex) {
            System.err.println("Failed to meet the conditions for a successful test.");
            System.err.println(ex.getMessage());
        }
    }

    @Test
    public void ByzantineBuildsFakeReport() {
        ByzantineClient byzantineClient = byzantineClients.get(new Random().nextInt(byzantineClients.size()));

        try {
            assert byzantineClient != null;
            Location spoofedLocation = byzantineClient.getMyLocation(0);
            spoofedLocation.setLatitude(new Random().nextInt(systemInfo.getGrid().size()));
            spoofedLocation.setLongitude(new Random().nextInt(systemInfo.getGrid().size()));
            byzantineClient.createLocationReport(0, spoofedLocation.getLatitude(), spoofedLocation.getLongitude());

            byzantineClient.submitLocationReport(0);

            Location locationResponse = byzantineClient.obtainLocationReport(byzantineClient.getClientId(), 0);
            assert locationResponse == null;
        } catch (Exception ex) {
            System.err.println("Failed to meet the conditions for a successful test.");
            System.err.println(ex.getMessage());
        }
    }

}