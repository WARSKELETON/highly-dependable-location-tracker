package pt.tecnico.hdlt.T25.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.*;
import pt.tecnico.hdlt.T25.client.Domain.ByzantineClient;
import pt.tecnico.hdlt.T25.client.Domain.Client;
import pt.tecnico.hdlt.T25.client.Domain.Location;
import pt.tecnico.hdlt.T25.client.Domain.LocationProof;
import pt.tecnico.hdlt.T25.server.Domain.ByzantineServer;
import pt.tecnico.hdlt.T25.server.Domain.Server;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class Stage2IT extends TestBase {
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

    @Test
    public void ByzantineClientInjectsFakeReportInByzantineServer() throws InterruptedException, GeneralSecurityException, IOException {
        ByzantineClient byzantineClient = byzantineClients.get(new ArrayList<>(byzantineClients.keySet()).get(new Random().nextInt(byzantineClients.keySet().size())));
        for (ByzantineClient bc : byzantineClients.values()) {
            if (bc.getClientId() == byzantineClient.getClientId()) {
                bc.setFlavor(ByzantineClient.Flavor.STUBBORN);
                continue;
            }

            bc.setFlavor(ByzantineClient.Flavor.CONSPIRATOR);
        }

        for (ByzantineServer byzantineServer : byzantineServers.values()) {
            byzantineServer.setFlavor(ByzantineServer.Flavor.UNVERIFIED);
        }

        assert byzantineClient != null;
        Location spoofedLocation = new Location(byzantineClient.getClientId(), 0, 0, 0);

        int gridIndex = new Random().nextInt(systemInfo.getGrid().size());
        spoofedLocation.setLatitude(systemInfo.getGrid().get(gridIndex).getLatitude());
        spoofedLocation.setLongitude(systemInfo.getGrid().get(gridIndex).getLongitude());
        System.out.println("byzantineUser" + byzantineClient.getClientId() + " building a fake report for spoofed location: " + spoofedLocation.getLatitude() + ", " + spoofedLocation.getLongitude());

        byzantineClient.createLocationReport(byzantineClient.getClientId(), 0, spoofedLocation.getLatitude(), spoofedLocation.getLongitude());
        boolean response = byzantineClient.submitLocationReportAtomic(0, 10);
        assertTrue(response);

        Location locationResponse = byzantineClient.obtainLocationReportAtomic(byzantineClient.getClientId(), 0, byzantineClient.getMaxTriesBeforeTimeout());

        Assertions.assertEquals(spoofedLocation.getUserId(), locationResponse.getUserId());
        Assertions.assertEquals(spoofedLocation.getEp(), locationResponse.getEp());
        Assertions.assertEquals(spoofedLocation.getLatitude(), locationResponse.getLatitude());
        Assertions.assertEquals(spoofedLocation.getLongitude(), locationResponse.getLongitude());

        System.out.println("HAClient obtains users for " + spoofedLocation.getEp() + " " + spoofedLocation.getLatitude() + ", " + spoofedLocation.getLongitude());
        List<Location> locationResponses = haClient.obtainUsersAtLocationRegular(spoofedLocation.getLatitude(), spoofedLocation.getLongitude(), spoofedLocation.getEp(), haClient.getMaxTriesBeforeTimeout());
        assertEquals(0, locationResponses.stream().filter(location -> location.getUserId() == spoofedLocation.getUserId()).count());

        System.out.println("HAClient location report for user" + byzantineClient.getClientId() + " at epoch " + spoofedLocation.getEp());
        Location haLocationResponse = haClient.obtainLocationReportAtomic(byzantineClient.getClientId(), 0, haClient.getMaxTriesBeforeTimeout());
    }

    @Test
    public void CrashServerWithWriteback() throws GeneralSecurityException, InterruptedException, IOException {
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

        servers.get(0).shutdownServer();
        System.out.println("Submitting report without server0");
        boolean response = testClient.submitLocationReportAtomic(0, 10);
        assertTrue(response);
        servers.get(2).shutdownServer();

        servers.put(0, new Server(0, systemInfo.getNumberOfUsers(), systemInfo.getStep(), maxByzantineUsers, maxNearbyByzantineUsers, maxReplicas, maxByzantineReplicas, "server0", true));

        System.out.println("Obtaining location report without server2");
        Location locationResponse = testClient.obtainLocationReportAtomic(testClient.getClientId(), 0, testClient.getMaxTriesBeforeTimeout());
        Assertions.assertEquals(originalLocation.getUserId(), locationResponse.getUserId());
        Assertions.assertEquals(originalLocation.getEp(), locationResponse.getEp());
        Assertions.assertEquals(originalLocation.getLatitude(), locationResponse.getLatitude());
        Assertions.assertEquals(originalLocation.getLongitude(), locationResponse.getLongitude());

        servers.put(2, new Server(2, systemInfo.getNumberOfUsers(), systemInfo.getStep(), maxByzantineUsers, maxNearbyByzantineUsers, maxReplicas, maxByzantineReplicas, "server2", true));
    }

    @Test
    public void ByzantineClientFakeReportInByzantineServerFailingBRB() throws InterruptedException, GeneralSecurityException {
        ByzantineClient byzantineClient = byzantineClients.get(new ArrayList<>(byzantineClients.keySet()).get(new Random().nextInt(byzantineClients.keySet().size())));
        for (ByzantineClient bc : byzantineClients.values()) {
            if (bc.getClientId() == byzantineClient.getClientId()) {
                bc.setFlavor(ByzantineClient.Flavor.STUBBORN);
                continue;
            };

            bc.setFlavor(ByzantineClient.Flavor.CONSPIRATOR);
        }

        for (ByzantineServer byzantineServer : byzantineServers.values()) {
            byzantineServer.setFlavor(ByzantineServer.Flavor.BRBFOLLOWER);
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
    }

    @Test
    public void ByzantineClientSpoofsProofReportToByzantineServer() throws InterruptedException, GeneralSecurityException, IOException {
        ByzantineClient byzantineClient = byzantineClients.get(new ArrayList<>(byzantineClients.keySet()).get(new Random().nextInt(byzantineClients.keySet().size())));
        boolean selected = false;
        for (ByzantineClient bc : byzantineClients.values()) {
            bc.setVictimIds(new ArrayList<>(clients.keySet()));

            if (bc.getClientId() == byzantineClient.getClientId()) {
                bc.setFlavor(ByzantineClient.Flavor.STUBBORN);
                continue;
            }

            if (!selected) {
                bc.setFlavor(ByzantineClient.Flavor.IMPERSONATE_DETERMINISTIC);
                selected = true;
                continue;
            }
            bc.setFlavor(ByzantineClient.Flavor.CONSPIRATOR);
        }

        for (ByzantineServer byzantineServer : byzantineServers.values()) {
            byzantineServer.setFlavor(ByzantineServer.Flavor.UNVERIFIED);
        }

        assert byzantineClient != null;
        Location spoofedLocation = new Location(byzantineClient.getClientId(), 0, 0, 0);

        int gridIndex = new Random().nextInt(systemInfo.getGrid().size());
        spoofedLocation.setLatitude(systemInfo.getGrid().get(gridIndex).getLatitude());
        spoofedLocation.setLongitude(systemInfo.getGrid().get(gridIndex).getLongitude());
        System.out.println("byzantineUser" + byzantineClient.getClientId() + " building a fake report for spoofed location: " + spoofedLocation.getLatitude() + ", " + spoofedLocation.getLongitude());

        byzantineClient.createLocationReport(byzantineClient.getClientId(), 0, spoofedLocation.getLatitude(), spoofedLocation.getLongitude());

        boolean response = byzantineClient.submitLocationReportAtomic(0, 10);
        assertTrue(response);

        Location locationResponse = byzantineClient.obtainLocationReportAtomic(byzantineClient.getClientId(), 0, byzantineClient.getMaxTriesBeforeTimeout());

        Assertions.assertEquals(spoofedLocation.getUserId(), locationResponse.getUserId());
        Assertions.assertEquals(spoofedLocation.getEp(), locationResponse.getEp());
        Assertions.assertEquals(spoofedLocation.getLatitude(), locationResponse.getLatitude());
        Assertions.assertEquals(spoofedLocation.getLongitude(), locationResponse.getLongitude());

        Client client = clients.get(0);
        Set<Integer> eps = new HashSet<>();
        eps.add(0);

        servers.get(0).shutdownServer();

        List<LocationProof> locationProofs = client.requestMyProofsRegular(client.getClientId(), eps);

        Assertions.assertEquals(0, locationProofs.size());

        servers.get(0).startServer();
    }

    @Test
    public void CrashAllServers() throws GeneralSecurityException, InterruptedException, IOException {
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

        boolean response = testClient.submitLocationReportAtomic(0, 10);
        assertTrue(response);
        servers.get(2).shutdownServer();
        servers.get(0).shutdownServer();

        servers.put(0, new Server(0, systemInfo.getNumberOfUsers(), systemInfo.getStep(), maxByzantineUsers, maxNearbyByzantineUsers, maxReplicas, maxByzantineReplicas, "server0", true));
        servers.put(2, new Server(2, systemInfo.getNumberOfUsers(), systemInfo.getStep(), maxByzantineUsers, maxNearbyByzantineUsers, maxReplicas, maxByzantineReplicas, "server2", true));

        System.out.println("Obtaining location report");
        Location locationResponse = testClient.obtainLocationReportAtomic(testClient.getClientId(), 0, testClient.getMaxTriesBeforeTimeout());
        Assertions.assertEquals(originalLocation.getUserId(), locationResponse.getUserId());
        Assertions.assertEquals(originalLocation.getEp(), locationResponse.getEp());
        Assertions.assertEquals(originalLocation.getLatitude(), locationResponse.getLatitude());
        Assertions.assertEquals(originalLocation.getLongitude(), locationResponse.getLongitude());
    }

    @Test
    public void CorrectUserObtainsOwnProofs() throws GeneralSecurityException, InterruptedException, JsonProcessingException {
        Client testClient = null;
        for (Client client : clients.values()) {
            if (client.getNearbyUsers(client.getMyLocation(0)).size() >= client.getMaxByzantineUsers() + client.getMaxNearbyByzantineUsers()) {
                testClient = client;
                break;
            }
        }

        assert testClient != null;
        System.out.println("user" + testClient.getClientId() + " building a correct report.");

        boolean response = testClient.submitLocationReportAtomic(0, 10);
        assertTrue(response);

        int count = 0;
        Set<Integer> eps = new HashSet<>();
        eps.add(0);
        for (Integer witnessId : testClient.getNearbyUsers(testClient.getMyLocation(0))) {
            List<LocationProof> locationProofs = clients.get(witnessId).requestMyProofsRegular(witnessId, eps);
            if (locationProofs.size() > 0) count++;
        }

        Assertions.assertEquals(testClient.getMaxByzantineUsers(), count);
    }

    @Test
    public void DropReport() throws GeneralSecurityException, InterruptedException, IOException {
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

        for (Server server : servers.values()) {
            server.shutdownServer();
        }

        for (ByzantineServer byzantineServer : byzantineServers.values()) {
            byzantineServer.shutdownServer();
        }

        Client finalTestClient = testClient;
        Thread task = new Thread(() -> {
            try {
                boolean response = finalTestClient.submitLocationReportAtomic(0, finalTestClient.getMaxTriesBeforeTimeout());
                assertTrue(response);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        task.start();

        Thread.sleep(2000);

        for (Integer serverId : servers.keySet()) {
            servers.put(serverId, new Server(serverId, systemInfo.getNumberOfUsers(), systemInfo.getStep(), maxByzantineUsers, maxNearbyByzantineUsers, maxReplicas, maxByzantineReplicas, "server" + serverId, true));
        }

        for (Integer serverId : byzantineServers.keySet()) {
            servers.put(serverId, new ByzantineServer(serverId, systemInfo.getNumberOfUsers(), systemInfo.getStep(), maxByzantineUsers, maxNearbyByzantineUsers, maxReplicas, maxByzantineReplicas, "server" + serverId, true));
        }

        Thread.sleep(15000);

        Location locationResponse = testClient.obtainLocationReportAtomic(testClient.getClientId(), 0, testClient.getMaxTriesBeforeTimeout());
        Assertions.assertEquals(originalLocation.getUserId(), locationResponse.getUserId());
        Assertions.assertEquals(originalLocation.getEp(), locationResponse.getEp());
        Assertions.assertEquals(originalLocation.getLatitude(), locationResponse.getLatitude());
        Assertions.assertEquals(originalLocation.getLongitude(), locationResponse.getLongitude());
    }
}