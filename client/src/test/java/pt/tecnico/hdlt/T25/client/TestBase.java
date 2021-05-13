package pt.tecnico.hdlt.T25.client;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import pt.tecnico.hdlt.T25.client.Domain.ByzantineClient;
import pt.tecnico.hdlt.T25.client.Domain.Client;
import pt.tecnico.hdlt.T25.client.Domain.HAClient;
import pt.tecnico.hdlt.T25.client.Domain.SystemInfo;
import pt.tecnico.hdlt.T25.server.Domain.ByzantineServer;
import pt.tecnico.hdlt.T25.server.Domain.Server;

import java.io.IOException;
import java.util.*;

public class TestBase {
    private static final String TEST_PROP_FILE = "/test.properties";
    private static final String TEST_GRID_FILE = "/grid.json";

    static HAClient haClient;
    static Map<Integer, Client> clients;
    static Map<Integer, Server> servers;
    static Map<Integer, ByzantineClient> byzantineClients;
    static SystemInfo systemInfo;
    static int serverPort;

    @BeforeAll
    public static void oneTimeSetup() throws IOException {
        Properties testProps = new Properties();

        try {
            testProps.load(TestBase.class.getResourceAsStream(TEST_PROP_FILE));
            System.out.println("Test properties:");
            System.out.println(testProps);
        }catch (IOException e) {
            final String msg = String.format("Could not load properties file {}", TEST_PROP_FILE);
            System.out.println(msg);
            throw e;
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            systemInfo = objectMapper.readValue(TestBase.class.getResourceAsStream(TEST_GRID_FILE), SystemInfo.class);
            List<Integer> byzantineIds = new ArrayList<>();
            byzantineIds.add(15);
            byzantineIds.add(17);
            byzantineIds.add(30);
            byzantineIds.add(44);
            byzantineIds.add(4);

            final String serverHost = testProps.getProperty("server.host");
            serverPort = Integer.parseInt(testProps.getProperty("server.port"));
            final int maxByzantineUsers = Integer.parseInt(testProps.getProperty("maxByzantineUsers"));
            final int maxNearbyByzantineUsers = Integer.parseInt(testProps.getProperty("maxNearbyByzantineUsers"));
            final int maxReplicas = Integer.parseInt(testProps.getProperty("server.maxReplicas"));
            final int maxByzantineReplicas = Integer.parseInt(testProps.getProperty("server.maxByzantineReplicas"));

            servers = new HashMap<>();
            Thread task = new Thread(() -> {
                try {
                    for (int i = 0; i < maxReplicas; i++) {
                        System.out.println("Starting server with id " + i);
                        if (i != 3) {
                            servers.put(i, new Server(i, systemInfo.getNumberOfUsers(), systemInfo.getStep(), maxByzantineUsers, maxNearbyByzantineUsers, maxReplicas, maxByzantineReplicas));
                        } else {
                            servers.put(i, new ByzantineServer(i, systemInfo.getNumberOfUsers(), systemInfo.getStep(), maxByzantineUsers, maxNearbyByzantineUsers, maxReplicas, maxByzantineReplicas));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            task.start();

            clients = new HashMap<>();
            byzantineClients = new HashMap<>();
            haClient = new HAClient(serverHost, serverPort, -1, systemInfo, true, maxByzantineUsers, maxReplicas, maxByzantineReplicas);
            for (int i = 0; i < systemInfo.getNumberOfUsers(); i++) {
                if (byzantineIds.contains(i)) {
                    byzantineClients.put(i, new ByzantineClient(serverHost, serverPort, i, systemInfo, maxByzantineUsers, maxNearbyByzantineUsers, ByzantineClient.Flavor.SILENT, true, maxReplicas, maxByzantineReplicas));
                } else {
                    clients.put(i, new Client(serverHost, serverPort, i, systemInfo, maxByzantineUsers, maxNearbyByzantineUsers, true, maxReplicas, maxByzantineReplicas));
                }
            }
        }

        catch (Exception e) {
            System.out.println(e.getMessage());
            System.err.println("Failed tests");
        }
    }

    @AfterAll
    public static void cleanup() {

    }

}