package pt.tecnico.hdlt.T25.client;

import pt.tecnico.hdlt.T25.Message;
import pt.tecnico.hdlt.T25.MessageServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static pt.tecnico.hdlt.T25.crypto.Crypto.*;

public class Client {

	public static void main(String[] args) throws Exception {

	    String clientPrivKeyFileName = "client-priv.key";
	    String serverPubKeyFileName = "pub.key";

        System.out.println(Client.class.getSimpleName());

		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		if (args.length < 2) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s host port%n", Client.class.getName());
			return;
		}

		final String host = args[0];
		final int port = Integer.parseInt(args[1]);
		final String target = host + ":" + port;

		// Channel is the abstraction to connect to a service endpoint
		// Let us use plaintext communication because we do not have certificates
		final ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();

		MessageServiceGrpc.MessageServiceBlockingStub stub = MessageServiceGrpc.newBlockingStub(channel);

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
	}

}
