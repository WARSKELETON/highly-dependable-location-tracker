package pt.tecnico.hdlt.T25.server.Services;

import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.LocationServerServiceGrpc;

import io.grpc.stub.StreamObserver;
import pt.tecnico.hdlt.T25.server.Domain.Server;

import java.util.logging.Logger;

public class LocationServerServiceImpl extends LocationServerServiceGrpc.LocationServerServiceImplBase {
	private static final Logger LOGGER = Logger.getLogger(LocationServerServiceImpl.class.getName());
	private final Server locationServer;

	public LocationServerServiceImpl(Server server) {
		locationServer = server;
	}

	@Override
	public void submitLocationReport(LocationServer.SubmitLocationReportRequest request, StreamObserver<LocationServer.SubmitLocationReportResponse> responseObserver) {
		try {
			if (locationServer.verifyLocationReport(request)) {
				System.out.println("Location report submitted with success.");
			} else {
				System.out.println("Location report illegitimate or already in the system.");
			}

			// TODO Change response
			LocationServer.SubmitLocationReportResponse response = LocationServer.SubmitLocationReportResponse.newBuilder()
					.setContent("content")
					.setSignature("signature")
					.build();

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
	}

	@Override
	public void obtainLocationReport(LocationServer.ObtainLocationReportRequest request, StreamObserver<LocationServer.ObtainLocationReportResponse> responseObserver) {
		try {
			LocationServer.ObtainLocationReportResponse response = locationServer.obtainLocationReport(request);

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
	}

	@Override
	public void obtainUsersAtLocation(LocationServer.ObtainUsersAtLocationRequest request, StreamObserver<LocationServer.ObtainUsersAtLocationResponse> responseObserver) {
		try {
			LocationServer.ObtainUsersAtLocationResponse response = locationServer.obtainUsersAtLocation(request);

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
		}
	}
}
