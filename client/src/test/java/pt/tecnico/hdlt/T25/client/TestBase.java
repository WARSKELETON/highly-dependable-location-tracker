package pt.tecnico.hdlt.T25.client;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import pt.tecnico.hdlt.T25.client.Domain.Client;
import pt.tecnico.hdlt.T25.client.Domain.SystemInfo;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

public class TestBase {
    private static final String TEST_PROP_FILE = "/test.properties";
    private static final String TEST_GRID_FILE = "/grid.json";

    private static Properties testProps;

    static Client client;

    @BeforeAll
    public static void oneTimeSetup() throws IOException {
        testProps = new Properties();

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
            SystemInfo systemInfo = objectMapper.readValue(new File("resources" + TEST_GRID_FILE), SystemInfo.class);

            final String serverHost = testProps.getProperty("server.host");
            final String serverPort = testProps.getProperty("server.port");
            final String clientId = testProps.getProperty("client.id");
            final int maxNearbyByzantineUsers = Integer.parseInt(testProps.getProperty("maxNearbyByzantineUsers"));

            client = new Client(serverHost, Integer.parseInt(serverPort), Integer.parseInt(clientId), systemInfo, maxNearbyByzantineUsers);
        }
        catch (IOException e) {
            System.err.println("Failed tests");
        }
    }

    @AfterAll
    public static void cleanup() {

    }

}