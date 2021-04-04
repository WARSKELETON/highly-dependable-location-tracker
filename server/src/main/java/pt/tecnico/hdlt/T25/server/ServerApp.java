package pt.tecnico.hdlt.T25.server;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pt.tecnico.hdlt.T25.server.Domain.Server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ServerApp {
	public static String SERVER_RECOVERY_FILE_PATH = "target/server_save.json";

	public static void main(String[] args) throws Exception {
		System.out.println(ServerApp.class.getSimpleName());
		Server recoverServer = recoverServerState();
		if (recoverServer == null) {
			System.out.println("Failed! Creating a new server");

			System.out.printf("Received %d arguments%n", args.length);
			for (int i = 0; i < args.length; i++) {
				System.out.printf("arg[%d] = %s%n", i, args[i]);
			}

			if (args.length < 2) {
				System.err.println("Argument(s) missing!");
				System.err.printf("Usage: java %s port%n", io.grpc.Server.class.getName());
				return;
			}

			final int port = Integer.parseInt(args[0]);
			final int numberOfUsers = Integer.parseInt(args[1]);
			final int step = Integer.parseInt(args[2]);

			Server server = new Server(port, numberOfUsers, step);
		}
	}

	private static Server recoverServerState() {
		try {
			System.out.println("Trying to recover previous server state");
			byte[] encoded = Files.readAllBytes(Paths.get(SERVER_RECOVERY_FILE_PATH));
			String content = new String(encoded, StandardCharsets.US_ASCII);

			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
			ObjectNode node = objectMapper.readValue(content, ObjectNode.class);
			return new Server(node);
		} catch (IOException|InterruptedException e) {
			return null;
		}
	}
}