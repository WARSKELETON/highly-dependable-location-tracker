package pt.tecnico.hdlt.T25.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.tecnico.hdlt.T25.client.Domain.ByzantineClient;
import pt.tecnico.hdlt.T25.client.Domain.Client;
import pt.tecnico.hdlt.T25.client.Domain.HAClient;
import pt.tecnico.hdlt.T25.client.Domain.SystemInfo;

import java.io.File;
import java.util.Arrays;

public class ClientApp {

	public static void main(String[] args) throws Exception {
        System.out.println(ClientApp.class.getSimpleName());

		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		if (args.length < 8) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s serverHost serverPort clientId maxByzantineUsers maxNearbyByzantineUsers maxReplicas maxByzantineReplicas keystorePassword%n", ClientApp.class.getName());
			return;
		}

		final String serverHost = args[0];
		final int serverPort = Integer.parseInt(args[1]);
		final int clientId = Integer.parseInt(args[2]);
		final int maxByzantineUsers = Integer.parseInt(args[3]);
		final int maxNearbyByzantineUsers = Integer.parseInt(args[4]);
		final int maxReplicas = Integer.parseInt(args[5]);
		final int maxByzantineReplicas = Integer.parseInt(args[6]);
		final String keystorePassword = args[8];

		ObjectMapper objectMapper = new ObjectMapper();
		SystemInfo systemInfo = objectMapper.readValue(new File("resources/grid.json"), SystemInfo.class);

		try {
			if (clientId == -1) {
				new HAClient(serverHost, serverPort, clientId, systemInfo, false, maxByzantineUsers, maxReplicas, maxByzantineReplicas, keystorePassword);
			} else {
				if (args.length == 8 && Boolean.parseBoolean(args[7])) {
					System.err.println("I am a byzantine user");
					new ByzantineClient(serverHost, serverPort, clientId, systemInfo, maxByzantineUsers, maxNearbyByzantineUsers, ByzantineClient.Flavor.CONSPIRATOR, false, maxReplicas, maxByzantineReplicas, keystorePassword);
				} else {
					new Client(serverHost, serverPort, clientId, systemInfo, maxByzantineUsers, maxNearbyByzantineUsers, false, maxReplicas, maxByzantineReplicas, keystorePassword);
				}
			}
		} catch (JsonProcessingException ex) {
			System.err.println("Client crashed due to some internal error.");
		}


	}
}
