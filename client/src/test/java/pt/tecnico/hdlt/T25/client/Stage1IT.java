package pt.tecnico.hdlt.T25.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.hdlt.T25.client.Domain.ByzantineClient;
import pt.tecnico.hdlt.T25.client.Domain.Client;
import pt.tecnico.hdlt.T25.client.Domain.Location;
import pt.tecnico.hdlt.T25.server.Domain.ByzantineServer;
import pt.tecnico.hdlt.T25.server.Domain.Server;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Stage1IT extends TestBase {
    // initialization and clean-up for each test

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
        for (Server server : servers.values()) {
            server.cleanUp();
        }

        for (ByzantineServer byzantineServer : byzantineServers.values()) {
            byzantineServer.cleanUp();
        }

        haClient.cleanup();

        for (Client client : clients.values()) {
            client.cleanup();
        }

        for (ByzantineClient byzantineClient : byzantineClients.values()) {
            byzantineClient.setFlavor(ByzantineClient.Flavor.SILENT);
            byzantineClient.cleanup();
        }
    }

    // tests
    @Test
    public void CorrectReportWithSufficientNeighbors() throws GeneralSecurityException, InterruptedException, JsonProcessingException {
        Client testClient = null;
        for (Client client : clients.values()) {
            if (client.getNearbyUsers(client.getMyLocation(0)).size() >= client.getMaxByzantineUsers() + client.getMaxNearbyByzantineUsers()) {
                testClient = client;
                break;
            }
        }

        assert testClient != null;
        System.out.println("user" + testClient.getClientId() + " building a correct report.");
        Location originalLocation = testClient.getMyLocation(0);

        boolean response = testClient.submitLocationReportAtomic(0, testClient.getMaxTriesBeforeTimeout());
        assertTrue(response);

        Location locationResponse = testClient.obtainLocationReportAtomic(testClient.getClientId(), 0, testClient.getMaxTriesBeforeTimeout());
        Assertions.assertEquals(originalLocation.getUserId(), locationResponse.getUserId());
        Assertions.assertEquals(originalLocation.getEp(), locationResponse.getEp());
        Assertions.assertEquals(originalLocation.getLatitude(), locationResponse.getLatitude());
        Assertions.assertEquals(originalLocation.getLongitude(), locationResponse.getLongitude());
        System.out.println("HAClient obtains report.");
        Location locationResponse1 = haClient.obtainLocationReportAtomic(testClient.getClientId(), 0, haClient.getMaxTriesBeforeTimeout());
        Assertions.assertEquals(originalLocation.getUserId(), locationResponse1.getUserId());
        Assertions.assertEquals(originalLocation.getEp(), locationResponse1.getEp());
        Assertions.assertEquals(originalLocation.getLatitude(), locationResponse1.getLatitude());
        Assertions.assertEquals(originalLocation.getLongitude(), locationResponse1.getLongitude());
    }

    @Test
    public void AutomaticCorrectReportWithReplayAndSufficientNeighbors() throws GeneralSecurityException, InterruptedException, JsonProcessingException {
        Map<Client, Integer> testClients = new HashMap<>();
        for (int ep = 0; ep < systemInfo.getMaxEp(); ep++) {
            Client testClient = null;
            for (Client client : clients.values()) {
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

            boolean response = testClient.submitLocationReportAtomic(ep, testClient.getMaxTriesBeforeTimeout());
            assertTrue(response);

            Location locationResponse = testClient.obtainLocationReportAtomic(testClient.getClientId(), ep, testClient.getMaxTriesBeforeTimeout());
            Assertions.assertEquals(originalLocation.getUserId(), locationResponse.getUserId());
            Assertions.assertEquals(originalLocation.getEp(), locationResponse.getEp());
            Assertions.assertEquals(originalLocation.getLatitude(), locationResponse.getLatitude());
            Assertions.assertEquals(originalLocation.getLongitude(), locationResponse.getLongitude());
            System.out.println();
            System.out.println("HAClient obtains report.");
            Location locationResponse1 = haClient.obtainLocationReportAtomic(testClient.getClientId(), ep, haClient.getMaxTriesBeforeTimeout());
            Assertions.assertEquals(originalLocation.getUserId(), locationResponse1.getUserId());
            Assertions.assertEquals(originalLocation.getEp(), locationResponse1.getEp());
            Assertions.assertEquals(originalLocation.getLatitude(), locationResponse1.getLatitude());
            Assertions.assertEquals(originalLocation.getLongitude(), locationResponse1.getLongitude());
        }

        System.out.println();
        for (Client testClient : testClients.keySet()) {
            int ep = testClients.get(testClient);
            System.out.println("user" + testClient.getClientId() + " replaying report at epoch " + ep);
            boolean response = testClient.submitLocationReportAtomic(ep, testClient.getMaxTriesBeforeTimeout());
            assertTrue(response);
        }
    }

    @Test
    public void AutomaticCorrectReportAllClientsWithSufficientNeighbors() throws GeneralSecurityException, InterruptedException, JsonProcessingException {
        for (int ep = 0; ep < systemInfo.getMaxEp(); ep++) {
            for (Client client : clients.values()) {
                if (client.getNearbyUsers(client.getMyLocation(ep)).size() >= client.getMaxByzantineUsers() + client.getMaxNearbyByzantineUsers()) {
                    System.out.println();
                    System.out.println("user" + client.getClientId() + " building a correct report at epoch " + ep);
                    Location originalLocation = client.getMyLocation(ep);

                    boolean response = client.submitLocationReportAtomic(ep, client.getMaxTriesBeforeTimeout());
                    assertTrue(response);

                    Location locationResponse = client.obtainLocationReportAtomic(client.getClientId(), ep, client.getMaxTriesBeforeTimeout());
                    Assertions.assertEquals(originalLocation.getUserId(), locationResponse.getUserId());
                    Assertions.assertEquals(originalLocation.getEp(), locationResponse.getEp());
                    Assertions.assertEquals(originalLocation.getLatitude(), locationResponse.getLatitude());
                    Assertions.assertEquals(originalLocation.getLongitude(), locationResponse.getLongitude());
                    System.out.println();
                    System.out.println("HAClient obtains report.");
                    Location locationResponse1 = haClient.obtainLocationReportAtomic(client.getClientId(), ep, haClient.getMaxTriesBeforeTimeout());
                    Assertions.assertEquals(originalLocation.getUserId(), locationResponse1.getUserId());
                    Assertions.assertEquals(originalLocation.getEp(), locationResponse1.getEp());
                    Assertions.assertEquals(originalLocation.getLatitude(), locationResponse1.getLatitude());
                    Assertions.assertEquals(originalLocation.getLongitude(), locationResponse1.getLongitude());
                    System.out.println();
                    System.out.println("HAClient obtains users for " + originalLocation.getEp() + " " + originalLocation.getLatitude() + ", " + originalLocation.getLongitude());
                    List<Location> locationResponses = haClient.obtainUsersAtLocationRegular(originalLocation.getLatitude(), originalLocation.getLongitude(), originalLocation.getEp(), haClient.getMaxTriesBeforeTimeout());
                    assertEquals(1, locationResponses.stream().filter(location -> location.getUserId() == originalLocation.getUserId()).count());
                }
            }
        }
    }

    @Test
    public void ByzantineBuildsFakeReport() throws InterruptedException, GeneralSecurityException, JsonProcessingException {
        ByzantineClient byzantineClient = byzantineClients.get(new ArrayList<>(byzantineClients.keySet()).get(new Random().nextInt(byzantineClients.keySet().size())));
        for (ByzantineClient bc : byzantineClients.values()) {
            if (bc.getClientId() == byzantineClient.getClientId()) continue;

            bc.setFlavor(ByzantineClient.Flavor.CONSPIRATOR);
        }

        assert byzantineClient != null;
        Location spoofedLocation = new Location(byzantineClient.getClientId(), 0, 0, 0);

        int gridIndex = new Random().nextInt(systemInfo.getGrid().size());
        spoofedLocation.setLatitude(systemInfo.getGrid().get(gridIndex).getLatitude());
        spoofedLocation.setLongitude(systemInfo.getGrid().get(gridIndex).getLongitude());
        System.out.println("byzantineUser" + byzantineClient.getClientId() + " building a fake report for spoofed location: " + spoofedLocation.getLatitude() + ", " + spoofedLocation.getLongitude());

        byzantineClient.createLocationReport(byzantineClient.getClientId(), 0, spoofedLocation.getLatitude(), spoofedLocation.getLongitude());

        boolean response = byzantineClient.submitLocationReportAtomic(0, 1);
        assertFalse(response);

        byzantineClient.obtainLocationReportAtomic(byzantineClient.getClientId(), 0, byzantineClient.getMaxTriesBeforeTimeout());
    }

    @Test
    public void ByzantineBuildsReportOnBehalfOfAllPossibleUsers() throws InterruptedException, GeneralSecurityException {
        ByzantineClient byzantineClient = byzantineClients.get(new ArrayList<>(byzantineClients.keySet()).get(new Random().nextInt(byzantineClients.keySet().size())));
        // Setting all byzantines as conspirators to allow collaboration
        for (ByzantineClient bc : byzantineClients.values()) {
            if (bc.getClientId() == byzantineClient.getClientId()) continue;

            bc.setFlavor(ByzantineClient.Flavor.CONSPIRATOR);
        }

        assert byzantineClient != null;

        for (Client victimClient : clients.values()) {
            if (victimClient.getNearbyUsers(victimClient.getMyLocation(0)).size() >= victimClient.getMaxByzantineUsers() + victimClient.getMaxNearbyByzantineUsers()) {
                Location victimLocation = victimClient.getMyLocation(0);
                System.out.println("byzantineUser" + byzantineClient.getClientId() + " building a fake report as " + victimClient.getClientId() + " with location: " + victimLocation.getLatitude() + ", " + victimLocation.getLongitude());
                byzantineClient.createLocationReport(victimClient.getClientId(), 0, victimLocation.getLatitude(), victimLocation.getLongitude());

                boolean response = byzantineClient.submitLocationReportAtomic(0, byzantineClient.getMaxTriesBeforeTimeout());
                assertFalse(response);
            }
        }
    }

    @Test
    public void ByzantineCreatesProofOnBehalfOfCorrectUser() throws GeneralSecurityException, JsonProcessingException, InterruptedException {
        Client testClient = null;
        List<Integer> byzantineIds = byzantineClients.values().stream().map(ByzantineClient::getClientId).collect(Collectors.toList());
        ByzantineClient testByzantineClient = null;
        for (ByzantineClient byzantineClient : byzantineClients.values()) {
            // Getting the legitimate clients nearby byzantines
            List<Integer> nearbyClientIds = byzantineClient.getNearbyUsers(byzantineClient.getMyLocation(0))
                    .stream()
                    .filter(clientId -> !byzantineIds.contains(clientId))
                    .collect(Collectors.toList());

            // Obtaining a correct neighbour client with sufficient neighbours
            for (int clientId : nearbyClientIds) {
                Client tmpClient = clients.get(clientId);
                if (tmpClient.getNearbyUsers(tmpClient.getMyLocation(0)).size() >= byzantineClient.getMaxByzantineUsers() + byzantineClient.getMaxNearbyByzantineUsers()) {
                    testClient = tmpClient;
                    byzantineClient.setFlavor(ByzantineClient.Flavor.IMPERSONATE);
                    testByzantineClient = byzantineClient;
                    break;
                }
            }

            if (testClient != null) break;
        }

        assert testClient != null;
        Location originalLocation = testClient.getMyLocation(0);
        System.out.println("user" + testClient.getClientId() + " building a correct report with byzantineUser" + testByzantineClient.getClientId() + " generating a proof on behalf of a correct user.");

        boolean response = testClient.submitLocationReportAtomic(0, testClient.getMaxTriesBeforeTimeout());
        assertTrue(response);

        Location locationResponse = testClient.obtainLocationReportAtomic(testClient.getClientId(), 0, testClient.getMaxTriesBeforeTimeout());
        Assertions.assertEquals(originalLocation.getUserId(), locationResponse.getUserId());
        Assertions.assertEquals(originalLocation.getEp(), locationResponse.getEp());
        Assertions.assertEquals(originalLocation.getLatitude(), locationResponse.getLatitude());
        Assertions.assertEquals(originalLocation.getLongitude(), locationResponse.getLongitude());
    }

    @Test
    public void ByzantineObtainsLocationFromOtherUser() throws GeneralSecurityException, InterruptedException, JsonProcessingException {
        Client testClient = null;
        ByzantineClient byzantineClient = byzantineClients.get(new ArrayList<>(byzantineClients.keySet()).get(new Random().nextInt(byzantineClients.keySet().size())));
        for (Client client : clients.values()) {
            if (client.getNearbyUsers(client.getMyLocation(0)).size() >= client.getMaxByzantineUsers() + client.getMaxNearbyByzantineUsers()) {
                testClient = client;
                break;
            }
        }

        assert testClient != null;
        int testClientId = testClient.getClientId();
        System.out.println("user" + testClient.getClientId() + " building a correct report.");
        boolean response = testClient.submitLocationReportAtomic(0, testClient.getMaxTriesBeforeTimeout());
        assertTrue(response);

        System.out.println("byzantineUser" + byzantineClient.getClientId() + " tries to obtain location from user" + testClient.getClientId());

        Location location = byzantineClient.obtainLocationReportAtomic(testClientId, 0, byzantineClient.getMaxTriesBeforeTimeout());
        assertNull(location);
    }

    @Test
    public void ByzantineObtainsUsersAtLocation() throws GeneralSecurityException, InterruptedException {
        Client testClient = null;
        ByzantineClient byzantineClient = byzantineClients.get(new ArrayList<>(byzantineClients.keySet()).get(new Random().nextInt(byzantineClients.keySet().size())));
        for (Client client : clients.values()) {
            if (client.getNearbyUsers(client.getMyLocation(0)).size() >= client.getMaxByzantineUsers() + client.getMaxNearbyByzantineUsers()) {
                testClient = client;
                break;
            }
        }

        assert testClient != null;
        System.out.println("user" + testClient.getClientId() + " building a correct report.");
        boolean response = testClient.submitLocationReportAtomic(0, testClient.getMaxTriesBeforeTimeout());
        assertTrue(response);

        Location location = testClient.getMyLocation(0);

        System.out.println("byzantineUser" + byzantineClient.getClientId() + " tries to obtain users at location " + location.getLatitude() + ", " + location.getLongitude());

        List<Location> locations = byzantineClient.obtainUsersAtLocationRegular(location.getLatitude(), location.getLongitude(), 0, byzantineClient.getMaxTriesBeforeTimeout());
        Assertions.assertEquals(0, locations.size());
    }

    @Test
    public void ReplaySubmissionReport() throws GeneralSecurityException, InterruptedException {
        Client testClient = null;
        for (Client client : clients.values()) {
            if (client.getNearbyUsers(client.getMyLocation(0)).size() >= client.getMaxByzantineUsers() + client.getMaxNearbyByzantineUsers()) {
                testClient = client;
                break;
            }
        }

        assert testClient != null;
        System.out.println("user" + testClient.getClientId() + " submitting multiple correct reports from the same epoch.");

        boolean response = testClient.submitLocationReportAtomic(0, testClient.getMaxTriesBeforeTimeout());
        assertTrue(response);

        response = testClient.submitLocationReportAtomic(0, testClient.getMaxTriesBeforeTimeout());
        assertTrue(response);
    }
}