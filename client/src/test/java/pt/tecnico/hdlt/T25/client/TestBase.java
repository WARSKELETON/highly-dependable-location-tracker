package pt.tecnico.hdlt.T25.client;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import pt.tecnico.hdlt.T25.client.Domain.ByzantineClient;
import pt.tecnico.hdlt.T25.client.Domain.Client;
import pt.tecnico.hdlt.T25.client.Domain.SystemInfo;

import java.io.IOException;
import java.util.*;

public class TestBase {
    private static final String TEST_PROP_FILE = "/test.properties";
    private static final String TEST_GRID_FILE = "/grid.json";

    static List<Client> clients;
    static List<ByzantineClient> byzantineClients;
    static SystemInfo systemInfo;

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
            Map<Integer, ByzantineClient.Flavor> byzantineFlavors = new HashMap<>();
            byzantineFlavors.put(15, ByzantineClient.Flavor.SILENT);
            byzantineFlavors.put(17, ByzantineClient.Flavor.SILENT);

            final String serverHost = testProps.getProperty("server.host");
            final String serverPort = testProps.getProperty("server.port");
            final int maxNearbyByzantineUsers = Integer.parseInt(testProps.getProperty("maxNearbyByzantineUsers"));

            clients = new ArrayList<>();
            byzantineClients = new ArrayList<>();
            for (int i = 0; i < systemInfo.getNumberOfUsers(); i++) {
                if (byzantineFlavors.containsKey(i)) {
                    byzantineClients.add(new ByzantineClient(serverHost, Integer.parseInt(serverPort), i, systemInfo, maxNearbyByzantineUsers, byzantineFlavors.get(i), true));
                } else {
                    clients.add(new Client(serverHost, Integer.parseInt(serverPort), i, systemInfo, maxNearbyByzantineUsers, true));
                }
            }
        }
        catch (IOException e) {
            System.out.println(e.getMessage());
            System.err.println("Failed tests");
        }
    }

    @AfterAll
    public static void cleanup() {

    }

}