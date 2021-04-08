package pt.tecnico.hdlt.T25.server.Services;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.grpc.Status;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.LocationServerServiceGrpc;

import io.grpc.stub.StreamObserver;
import pt.tecnico.hdlt.T25.server.Domain.Exceptions.DuplicateReportException;
import pt.tecnico.hdlt.T25.server.Domain.Exceptions.InvalidNumberOfProofsException;
import pt.tecnico.hdlt.T25.server.Domain.Exceptions.InvalidSignatureException;
import pt.tecnico.hdlt.T25.server.Domain.Exceptions.ReportNotFoundException;
import pt.tecnico.hdlt.T25.server.Domain.Server;

import java.io.IOException;
import java.security.GeneralSecurityException;
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
			LocationServer.SubmitLocationReportResponse response = locationServer.submitLocationReport(request);

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (DuplicateReportException ex) {
			responseObserver.onError(Status.ALREADY_EXISTS.withDescription(ex.getMessage()).asRuntimeException());
		} catch (InvalidNumberOfProofsException ex2) {
			responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex2.getMessage()).asRuntimeException());
		} catch (InvalidSignatureException ex3) {
			responseObserver.onError(Status.UNAUTHENTICATED.withDescription(ex3.getMessage()).asRuntimeException());
		} catch (GeneralSecurityException | IOException ex4) {
			responseObserver.onError(Status.ABORTED.withDescription(ex4.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void obtainLocationReport(LocationServer.ObtainLocationReportRequest request, StreamObserver<LocationServer.ObtainLocationReportResponse> responseObserver) {
		try {
			LocationServer.ObtainLocationReportResponse response = locationServer.obtainLocationReport(request);

			responseObserver.onNext(response);
			responseObserver.onCompleted();

		} catch (ReportNotFoundException ex) {
			responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
		} catch (InvalidSignatureException ex2) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(ex2.getMessage()).asRuntimeException());
		} catch (GeneralSecurityException | JsonProcessingException ex3) {
			responseObserver.onError(Status.ABORTED.withDescription(ex3.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void obtainUsersAtLocation(LocationServer.ObtainUsersAtLocationRequest request, StreamObserver<LocationServer.ObtainUsersAtLocationResponse> responseObserver) {
		try {
			LocationServer.ObtainUsersAtLocationResponse response = locationServer.obtainUsersAtLocation(request);

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (InvalidSignatureException ex) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(ex.getMessage()).asRuntimeException());
		} catch (GeneralSecurityException | JsonProcessingException ex2) {
			responseObserver.onError(Status.ABORTED.withDescription(ex2.getMessage()).asRuntimeException());
		}
	}
}
