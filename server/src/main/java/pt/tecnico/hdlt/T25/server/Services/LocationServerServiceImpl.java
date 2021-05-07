package pt.tecnico.hdlt.T25.server.Services;

import io.grpc.Context;
import io.grpc.Status;
import pt.tecnico.hdlt.T25.LocationServer;
import pt.tecnico.hdlt.T25.LocationServerServiceGrpc;

import io.grpc.stub.StreamObserver;
import pt.tecnico.hdlt.T25.server.Domain.Exceptions.*;
import pt.tecnico.hdlt.T25.server.Domain.Server;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;

public class LocationServerServiceImpl extends LocationServerServiceGrpc.LocationServerServiceImplBase {
	private static final Logger LOGGER = Logger.getLogger(LocationServerServiceImpl.class.getName());
	private final Server locationServer;
	static int count = 0;

	public LocationServerServiceImpl(Server server) {
		locationServer = server;
	}

	@Override
	public void obtainLatestSeqNumber(LocationServer.ObtainLatestSeqNumberRequest request, StreamObserver<LocationServer.ObtainLatestSeqNumberResponse> responseObserver) {
		try {
			if (Context.current().isCancelled()) {
				System.out.println("TIMEOUT SERVER");
				responseObserver.onError(Status.CANCELLED.asRuntimeException());
				return;
			}
			LocationServer.ObtainLatestSeqNumberResponse response = locationServer.obtainLatestSeqNumber(request);

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (InvalidSignatureException ex3) {
			responseObserver.onError(Status.UNAUTHENTICATED.withDescription(ex3.getMessage()).asRuntimeException());
		} catch (GeneralSecurityException ex4) {
			responseObserver.onError(Status.ABORTED.withDescription(ex4.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void submitLocationReport(LocationServer.SubmitLocationReportRequest request, StreamObserver<LocationServer.SubmitLocationReportResponse> responseObserver) {
		try {
			if (Context.current().isCancelled()) {
				System.out.println("TIMEOUT SERVER");
				responseObserver.onError(Status.CANCELLED.asRuntimeException());
				return;
			}
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
            if (Context.current().isCancelled()) {
                System.out.println("TIMEOUT SERVER");
                responseObserver.onError(Status.CANCELLED.asRuntimeException());
                return;
            }
			LocationServer.ObtainLocationReportResponse response = locationServer.obtainLocationReport(request);

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (ReportNotFoundException ex) {
			responseObserver.onError(Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
		} catch (StaleException ex) {
			responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(ex.getMessage()).asRuntimeException());
		} catch (InvalidSignatureException ex2) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(ex2.getMessage()).asRuntimeException());
		} catch (IOException | GeneralSecurityException ex3) {
			responseObserver.onError(Status.ABORTED.withDescription(ex3.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void obtainUsersAtLocation(LocationServer.ObtainUsersAtLocationRequest request, StreamObserver<LocationServer.ObtainUsersAtLocationResponse> responseObserver) {
		try {
			if (Context.current().isCancelled()) {
				System.out.println("TIMEOUT SERVER");
				responseObserver.onError(Status.CANCELLED.asRuntimeException());
				return;
			}
			LocationServer.ObtainUsersAtLocationResponse response = locationServer.obtainUsersAtLocation(request);

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (StaleException ex) {
			responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(ex.getMessage()).asRuntimeException());
		} catch (InvalidSignatureException ex) {
			responseObserver.onError(Status.PERMISSION_DENIED.withDescription(ex.getMessage()).asRuntimeException());
		} catch (GeneralSecurityException | IOException ex2) {
			responseObserver.onError(Status.ABORTED.withDescription(ex2.getMessage()).asRuntimeException());
		}
	}
}
