package pt.tecnico.hdlt.T25.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import pt.tecnico.hdlt.T25.client.Domain.ByzantineClient;
import pt.tecnico.hdlt.T25.client.Domain.Client;
import pt.tecnico.hdlt.T25.client.Domain.HAClient;
import pt.tecnico.hdlt.T25.client.Domain.SystemInfo;

import java.io.File;

public class ClientApp {

	public static void main(String[] args) throws Exception {
        System.out.println(ClientApp.class.getSimpleName());

		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		if (args.length < 4) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s serverHost port clientId%n", ClientApp.class.getName());
			return;
		}

		final String serverHost = args[0];
		final int serverPort = Integer.parseInt(args[1]);
		final int clientId = Integer.parseInt(args[2]);
		final int maxNearbyByzantineUsers = Integer.parseInt(args[3]);

		ObjectMapper objectMapper = new ObjectMapper();
		SystemInfo systemInfo = objectMapper.readValue(new File("resources/grid.json"), SystemInfo.class);

		if (clientId == -1) {
			new HAClient(serverHost, serverPort, clientId, systemInfo);
		} else {
            if (args.length == 5 && Boolean.parseBoolean(args[4])) {
                new ByzantineClient(serverHost, serverPort, clientId, systemInfo);
            } else {
                new Client(serverHost, serverPort, clientId, systemInfo, maxNearbyByzantineUsers);
            }
		}
	}
}
