package pt.tecnico.hdlt.T25.server;

import pt.tecnico.hdlt.T25.server.Domain.Server;

public class ServerApp {

	public static void main(String[] args) throws Exception {
		System.out.println(ServerApp.class.getSimpleName());

		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		if (args.length < 1) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s port%n", io.grpc.Server.class.getName());
			return;
		}

		final int port = Integer.parseInt(args[0]);
		Server server = new Server(port);
	}

}
