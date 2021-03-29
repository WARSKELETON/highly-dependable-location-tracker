package pt.tecnico.hdlt.T25.server;

import io.grpc.BindableService;
import io.grpc.ServerBuilder;

public class Server {

	public static void main(String[] args) throws Exception {
		System.out.println(Server.class.getSimpleName());

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
		final BindableService impl = new MessageServiceImpl();

		io.grpc.Server server = ServerBuilder.forPort(port).addService(impl).build();

		server.start();

		System.out.println("Server started");
		server.awaitTermination();
	}

}