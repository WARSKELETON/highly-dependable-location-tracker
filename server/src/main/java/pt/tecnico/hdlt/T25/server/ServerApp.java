package pt.tecnico.hdlt.T25.server;

import pt.tecnico.hdlt.T25.server.Domain.Server;

public class ServerApp {

	public static String SERVER_RECOVERY_FILE_PATH = "resources/server_state.json";
	public static String BACKUP_RECOVERY_FILE_PATH = "resources/backup_state.json";

	public static void main(String[] args) throws Exception {
		System.out.println(ServerApp.class.getSimpleName());

		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		if (args.length < 4) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s port%n", io.grpc.Server.class.getName());
			return;
		}

		final int port = Integer.parseInt(args[0]);
		final int numberOfUsers = Integer.parseInt(args[1]);
		final int step = Integer.parseInt(args[2]);
		final int maxNearbyByzantineUsers = Integer.parseInt(args[3]);
		new Server(port, numberOfUsers, step, maxNearbyByzantineUsers);
	}
}
