package pt.tecnico.hdlt.T25.server;

import pt.tecnico.hdlt.T25.server.Domain.Server;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class ServerApp {

	public static String SERVER_RECOVERY_FILE_PATH = "resources/server_state.json";
	public static String BACKUP_RECOVERY_FILE_PATH = "resources/backup_state.json";

	public static void main(String[] args) {
		System.out.println(ServerApp.class.getSimpleName());

		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		if (args.length < 5) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s serverId numberOfUsers step maxByzantineUsers maxNearbyByzantineUsers%n", io.grpc.Server.class.getName());
			return;
		}

		final int serverId = Integer.parseInt(args[0]);
		final int numberOfUsers = Integer.parseInt(args[1]);
		final int step = Integer.parseInt(args[2]);
		final int maxByzantineUsers = Integer.parseInt(args[3]);
		final int maxNearbyByzantineUsers = Integer.parseInt(args[4]);

		try {
			new Server(serverId, numberOfUsers, step, maxByzantineUsers, maxNearbyByzantineUsers);
		} catch (IOException ex) {
			System.err.println("Server crashed due to some internal error.");
		} catch (GeneralSecurityException ex2) {
			System.err.println("Server crashed when trying to obtain keys.");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
