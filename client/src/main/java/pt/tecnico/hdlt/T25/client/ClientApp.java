package pt.tecnico.hdlt.T25.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import pt.tecnico.hdlt.T25.client.Domain.Client;
import pt.tecnico.hdlt.T25.client.Domain.SystemInfo;

import java.io.File;

public class ClientApp {

	public static void main(String[] args) throws Exception {

	    String clientPrivKeyFileName = "client-priv.key";
	    String serverPubKeyFileName = "keys/server-pub.key";

        System.out.println(ClientApp.class.getSimpleName());

		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		if (args.length < 3) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s serverHost port clientId%n", ClientApp.class.getName());
			return;
		}

		final String serverHost = args[0];
		final int serverPort = Integer.parseInt(args[1]);
		final int clientId = Integer.parseInt(args[2]);

		ObjectMapper objectMapper = new ObjectMapper();
		SystemInfo systemInfo = objectMapper.readValue(new File("resources/grid.json"), SystemInfo.class);

		Client client = new Client(serverHost, serverPort, clientId, systemInfo);

		/*
		RSAPublicKey serverPubKey = getPub(serverPubKeyFileName);
		RSAPrivateKey clientPrivKey = getPriv(clientPrivKeyFileName);
		String content = encryptAES("friend");
		Message.MessageRequest request = Message.MessageRequest.newBuilder()
				.setContent(content)
				.setSignature(sign(content, clientPrivKey))
				.build();

		Message.MessageResponse response = stub.greeting(request);
		String responseContent = response.getContent();
		String responseSignature = response.getSignature();
		try {
			if (verify(responseContent, responseSignature, serverPubKey)) {
				System.out.println("Signature verified! Received message -> " + decryptAES(responseContent));
			}
		} catch (Exception ignored) {
		}

		// A Channel should be shutdown before stopping the process.
		channel.shutdownNow();
		 */
	}

}
