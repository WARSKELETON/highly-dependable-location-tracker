package pt.tecnico.hdlt.T25.server.Services;

import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.LocationServerServiceGrpc;

import io.grpc.stub.StreamObserver;
import pt.tecnico.hdlt.T25.server.Domain.Server;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.logging.Logger;

import static pt.tecnico.hdlt.T25.crypto.Crypto.*;

public class LocationServerServiceImpl extends LocationServerServiceGrpc.LocationServerServiceImplBase {
	private static final Logger LOGGER = Logger.getLogger(LocationServerServiceImpl.class.getName());
	private final Server locationServer;

	public LocationServerServiceImpl(Server server) {
		locationServer = server;
	}

	@Override
	public void submitLocationReport(LocationServer.SubmitLocationReportRequest request, StreamObserver<LocationServer.SubmitLocationReportResponse> responseObserver) {
		String privKeyFileName = "priv.key";
		String pubKeyFileName = "client-pub.key";

		try {
			RSAPublicKey clientPublicKey = getPub(pubKeyFileName);
			RSAPrivateKey serverPrivateKey = getPriv(privKeyFileName);

			locationServer.verifyLocationReport(request);

			LocationServer.SubmitLocationReportResponse response = LocationServer.SubmitLocationReportResponse.newBuilder()
					.setContent("content")
					.setSignature("signature")
					.build();

/*			String requestContent = request.getContent();
			String requestSignature = request.getSignature();
			String decipheredContent = decryptAES(requestContent);
			if (verify(requestContent, requestSignature, clientPublicKey)) {
				System.out.println("Signature verified! Received message -> " + decipheredContent);
				String content = encryptAES(decipheredContent);
				response = LocationServer.SubmitLocationReportResponse.newBuilder()
						.setContent(content)
						.setSignature(sign(content, serverPrivateKey))
						.build();
			}*/

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
	}

	@Override
	public void obtainLocationReport(LocationServer.ObtainLocationReportRequest request, StreamObserver<LocationServer.ObtainLocationReportResponse> responseObserver) {
		String privKeyFileName = "priv.key";
		String pubKeyFileName = "client-pub.key";

		try {
			RSAPublicKey clientPublicKey = getPub(pubKeyFileName);
			RSAPrivateKey serverPrivateKey = getPriv(privKeyFileName);
			LocationServer.ObtainLocationReportResponse response = locationServer.obtainLocationReport(request);

/*			String requestContent = request.getContent();
			String requestSignature = request.getSignature();
			String decipheredContent = decryptAES(requestContent);
			if (verify(requestContent, requestSignature, clientPublicKey)) {
				System.out.println("Signature verified! Received message -> " + decipheredContent);
				String content = encryptAES(decipheredContent);
				response = LocationServer.SubmitLocationReportResponse.newBuilder()
						.setContent(content)
						.setSignature(sign(content, serverPrivateKey))
						.build();
			}*/

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (Exception ignored) {
			System.out.println("morri");
		}
	}
}
