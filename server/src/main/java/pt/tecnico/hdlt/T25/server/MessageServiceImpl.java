package pt.tecnico.hdlt.T25.server;

import pt.tecnico.hdlt.T25.Message;
import pt.tecnico.hdlt.T25.MessageServiceGrpc;

import io.grpc.stub.StreamObserver;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static pt.tecnico.hdlt.T25.crypto.Crypto.*;

public class MessageServiceImpl extends MessageServiceGrpc.MessageServiceImplBase {

	@Override
	public void greeting(Message.MessageRequest request, StreamObserver<Message.MessageResponse> responseObserver) {
		String privKeyFileName = "priv.key";
		String pubKeyFileName = "client-pub.key";

		try {
			RSAPublicKey clientPublicKey = getPub(pubKeyFileName);
			RSAPrivateKey serverPrivateKey = getPriv(privKeyFileName);
			Message.MessageResponse response = null;

			String requestContent = request.getContent();
			String requestSignature = request.getSignature();
			String decipheredContent = decryptAES(requestContent);
			if (verify(requestContent, requestSignature, clientPublicKey)) {
				System.out.println("Signature verified! Received message -> " + decipheredContent);
				String content = encryptAES(decipheredContent);
				response = Message.MessageResponse.newBuilder()
						.setContent(content)
						.setSignature(sign(content, serverPrivateKey))
						.build();
			}

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (Exception ignored) {
			System.out.println("morri");
		}
	}
}
