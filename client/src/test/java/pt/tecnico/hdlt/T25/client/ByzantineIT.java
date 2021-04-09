package pt.tecnico.hdlt.T25.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.*;
import pt.tecnico.hdlt.T25.client.Domain.ByzantineClient;
import pt.tecnico.hdlt.T25.client.Domain.Client;
import pt.tecnico.hdlt.T25.client.Domain.Location;

import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ByzantineIT extends TestBase {
    // initialization and clean-up for each test

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
        server.cleanUp();

        for (Client client : clients) {
            client.cleanup();
        }

        for (ByzantineClient byzantineClient : byzantineClients) {
            byzantineClient.setFlavor(ByzantineClient.Flavor.SILENT);
            byzantineClient.cleanup();
        }
    }

    // tests

    @Test
    public void CorrectReportWithSufficientNeighbors() throws GeneralSecurityException, InterruptedException, JsonProcessingException {
        Client testClient = null;
        for (Client client : clients) {
            if (client.getNearbyUsers(client.getMyLocation(0)).size() >= client.getMaxByzantineUsers() + client.getMaxNearbyByzantineUsers()) {
                testClient = client;
                break;
            }
        }

        assert testClient != null;
        System.out.println("user" + testClient.getClientId() + " building a correct report.");
        Location originalLocation = testClient.getMyLocation(0);

        testClient.submitLocationReport(0);

        Location locationResponse = testClient.obtainLocationReport(testClient.getClientId(), 0);
        Assertions.assertEquals(originalLocation.getUserId(), locationResponse.getUserId());
        Assertions.assertEquals(originalLocation.getEp(), locationResponse.getEp());
        Assertions.assertEquals(originalLocation.getLatitude(), locationResponse.getLatitude());
        Assertions.assertEquals(originalLocation.getLongitude(), locationResponse.getLongitude());
        System.out.println("HAClient obtains report.");
        Location locationResponse1 = haClient.obtainLocationReport(testClient.getClientId(), 0);
        Assertions.assertEquals(originalLocation.getUserId(), locationResponse1.getUserId());
        Assertions.assertEquals(originalLocation.getEp(), locationResponse1.getEp());
        Assertions.assertEquals(originalLocation.getLatitude(), locationResponse1.getLatitude());
        Assertions.assertEquals(originalLocation.getLongitude(), locationResponse1.getLongitude());
    }

    @Test
    public void AutomaticCorrectReportWithSufficientNeighbors() throws GeneralSecurityException, InterruptedException, JsonProcessingException {
        Map<Client, Integer> testClients = new HashMap<>();
        for (int ep = 0; ep < systemInfo.getMaxEp(); ep++) {
            Client testClient = null;
            for (Client client : clients) {
                if (client.getNearbyUsers(client.getMyLocation(ep)).size() >= client.getMaxByzantineUsers() + client.getMaxNearbyByzantineUsers()) {
                    testClient = client;
                    testClients.put(client, ep);
                    break;
                }
            }

            assert testClient != null;
            System.out.println();
            System.out.println("user" + testClient.getClientId() + " building a correct report at epoch " + ep);
            Location originalLocation = testClient.getMyLocation(ep);

            testClient.submitLocationReport(ep);

            Location locationResponse = testClient.obtainLocationReport(testClient.getClientId(), ep);
            Assertions.assertEquals(originalLocation.getUserId(), locationResponse.getUserId());
            Assertions.assertEquals(originalLocation.getEp(), locationResponse.getEp());
            Assertions.assertEquals(originalLocation.getLatitude(), locationResponse.getLatitude());
            Assertions.assertEquals(originalLocation.getLongitude(), locationResponse.getLongitude());
            System.out.println();
            System.out.println("HAClient obtains report.");
            Location locationResponse1 = haClient.obtainLocationReport(testClient.getClientId(), ep);
            Assertions.assertEquals(originalLocation.getUserId(), locationResponse1.getUserId());
            Assertions.assertEquals(originalLocation.getEp(), locationResponse1.getEp());
            Assertions.assertEquals(originalLocation.getLatitude(), locationResponse1.getLatitude());
            Assertions.assertEquals(originalLocation.getLongitude(), locationResponse1.getLongitude());
        }

        System.out.println();
        for (Client testClient : testClients.keySet()) {
            int ep = testClients.get(testClient);
            System.out.println("user" + testClient.getClientId() + " replaying report at epoch " + ep);
            Assertions.assertEquals(Status.Code.ALREADY_EXISTS,
                    assertThrows(StatusRuntimeException.class, () -> testClient.submitLocationReport(ep)).getStatus().getCode());
        }
    }

    @Test
    public void ByzantineBuildsFakeReport() throws InterruptedException{
        ByzantineClient byzantineClient = byzantineClients.get(new Random().nextInt(byzantineClients.size()));
        for (ByzantineClient bc : byzantineClients) {
            if (bc.getClientId() == byzantineClient.getClientId()) continue;

            bc.setFlavor(ByzantineClient.Flavor.CONSPIRATOR);
        }

        assert byzantineClient != null;
        Location spoofedLocation = new Location(byzantineClient.getClientId(), 0, 0, 0);

        int gridIndex = new Random().nextInt(systemInfo.getGrid().size());
        spoofedLocation.setLatitude(systemInfo.getGrid().get(gridIndex).getLatitude());
        spoofedLocation.setLongitude(systemInfo.getGrid().get(gridIndex).getLongitude());
        System.out.println("byzantineUser" + byzantineClient.getClientId() + " building a fake report for spoofed location: " + spoofedLocation.getLatitude() + ", " + spoofedLocation.getLongitude());

        byzantineClient.createLocationReport(0, spoofedLocation.getLatitude(), spoofedLocation.getLongitude());

        Assertions.assertEquals(Status.Code.INVALID_ARGUMENT,
                assertThrows(StatusRuntimeException.class, () -> byzantineClient.submitLocationReport(0)).getStatus().getCode());
    }

    @Test
    public void ByzantineCreatesProofOnBehalfOfCorrectUser() throws GeneralSecurityException, JsonProcessingException {
        Client testClient = null;
        List<Integer> byzantineIds = byzantineClients.stream().map(ByzantineClient::getClientId).collect(Collectors.toList());
        ByzantineClient testByzantineClient = null;
        for (ByzantineClient byzantineClient : byzantineClients) {
            List<Integer> nearbyClientIds = byzantineClient.getNearbyUsers(byzantineClient.getMyLocation(0))
                    .stream()
                    .filter(clientId -> !byzantineIds.contains(clientId))
                    .collect(Collectors.toList());

            if (nearbyClientIds.size() >= byzantineClient.getMaxByzantineUsers() + byzantineClient.getMaxNearbyByzantineUsers()) {
                testClient = clients.get(nearbyClientIds.get(new Random().nextInt(nearbyClientIds.size())));
                byzantineClient.setFlavor(ByzantineClient.Flavor.IMPERSONATE);
                testByzantineClient = byzantineClient;
                break;
            }
        }

        assert testClient != null;
        Location originalLocation = testClient.getMyLocation(0);
        System.out.println("user" + testClient.getClientId() + " building a correct report with byzantineUser" + testByzantineClient.getClientId() + " generating a proof on behalf of a correct user.");

        Client finalTestClient = testClient;
        Assertions.assertDoesNotThrow(() -> finalTestClient.submitLocationReport(0));

        Location locationResponse = testClient.obtainLocationReport(testClient.getClientId(), 0);
        Assertions.assertEquals(originalLocation.getUserId(), locationResponse.getUserId());
        Assertions.assertEquals(originalLocation.getEp(), locationResponse.getEp());
        Assertions.assertEquals(originalLocation.getLatitude(), locationResponse.getLatitude());
        Assertions.assertEquals(originalLocation.getLongitude(), locationResponse.getLongitude());
    }

    @Test
    public void ByzantineObtainsLocationFromOtherUser() throws GeneralSecurityException, InterruptedException {
        Client testClient = null;
        ByzantineClient byzantineClient = byzantineClients.get(new Random().nextInt(byzantineClients.size()));
        for (Client client : clients) {
            if (client.getNearbyUsers(client.getMyLocation(0)).size() >= client.getMaxByzantineUsers() + client.getMaxNearbyByzantineUsers()) {
                testClient = client;
                break;
            }
        }

        assert testClient != null;
        int testClientId = testClient.getClientId();
        System.out.println("user" + testClient.getClientId() + " building a correct report.");
        testClient.submitLocationReport(0);

        System.out.println("byzantineUser" + byzantineClient.getClientId() + " tries to obtain location from user" + testClient.getClientId());

        Assertions.assertEquals(Status.Code.PERMISSION_DENIED,
                assertThrows(StatusRuntimeException.class, () -> byzantineClient.obtainLocationReport(testClientId, 0)).getStatus().getCode());
    }

    @Test
    public void ByzantineObtainsUsersAtLocation() throws GeneralSecurityException, InterruptedException {
        Client testClient = null;
        ByzantineClient byzantineClient = byzantineClients.get(new Random().nextInt(byzantineClients.size()));
        for (Client client : clients) {
            if (client.getNearbyUsers(client.getMyLocation(0)).size() >= client.getMaxByzantineUsers() + client.getMaxNearbyByzantineUsers()) {
                testClient = client;
                break;
            }
        }

        assert testClient != null;
        System.out.println("user" + testClient.getClientId() + " building a correct report.");
        testClient.submitLocationReport(0);
        Location location = testClient.getMyLocation(0);

        System.out.println("byzantineUser" + byzantineClient.getClientId() + " tries to obtain users at location " + location.getLatitude() + ", " + location.getLongitude());

        Assertions.assertEquals(Status.Code.PERMISSION_DENIED,
                assertThrows(StatusRuntimeException.class, () -> byzantineClient.obtainUsersAtLocation(location.getLatitude(), location.getLongitude(), 0)).getStatus().getCode());
    }

    @Test
    public void ReplaySubmission() throws GeneralSecurityException, InterruptedException {
        Client testClient = null;
        for (Client client : clients) {
            if (client.getNearbyUsers(client.getMyLocation(0)).size() >= client.getMaxByzantineUsers() + client.getMaxNearbyByzantineUsers()) {
                testClient = client;
                break;
            }
        }

        assert testClient != null;
        System.out.println("user" + testClient.getClientId() + " submitting multiple correct reports from the same epoch.");

        testClient.submitLocationReport(0);
        Client finalTestClient = testClient;
        Assertions.assertEquals(Status.Code.ALREADY_EXISTS,
                assertThrows(StatusRuntimeException.class, () -> finalTestClient.submitLocationReport(0)).getStatus().getCode());
    }
}