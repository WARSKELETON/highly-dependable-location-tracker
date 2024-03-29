package pt.tecnico.hdlt.T25.server.Services;

import com.fasterxml.jackson.core.JsonProcessingException;
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
		} catch (JsonProcessingException|GeneralSecurityException ex4) {
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
			responseObserver.onError(locationServer.buildException(Status.ALREADY_EXISTS.getCode(), ex.getMessage(), locationServer.getUserIdFromSubmitLocationReportRequest(request), false));
		} catch (InvalidNumberOfProofsException ex2) {
			responseObserver.onError(locationServer.buildException(Status.INVALID_ARGUMENT.getCode(), ex2.getMessage(), locationServer.getUserIdFromSubmitLocationReportRequest(request), false));
		} catch (InvalidSignatureException | InvalidProofOfWorkException ex3) {
			responseObserver.onError(locationServer.buildException(Status.UNAUTHENTICATED.getCode(), ex3.getMessage(), locationServer.getUserIdFromSubmitLocationReportRequest(request), false));
		} catch (GeneralSecurityException | IOException | InterruptedException ex4) {
			responseObserver.onError(locationServer.buildException(Status.ABORTED.getCode(), ex4.getMessage(), locationServer.getUserIdFromSubmitLocationReportRequest(request), false));
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
			responseObserver.onError(locationServer.buildException(Status.NOT_FOUND.getCode(), ex.getMessage(), locationServer.getUserIdFromObtainLocationReportRequest(request), true));
		} catch (StaleException ex4) {
			responseObserver.onError(locationServer.buildException(Status.FAILED_PRECONDITION.getCode(), ex4.getMessage(), locationServer.getUserIdFromObtainLocationReportRequest(request), true));
		} catch (InvalidSignatureException ex2) {
			responseObserver.onError(locationServer.buildException(Status.PERMISSION_DENIED.getCode(), ex2.getMessage(), locationServer.getUserIdFromObtainLocationReportRequest(request), true));
		} catch (IOException | GeneralSecurityException ex3) {
			responseObserver.onError(locationServer.buildException(Status.ABORTED.getCode(), ex3.getMessage(), locationServer.getUserIdFromObtainLocationReportRequest(request), true));
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
			responseObserver.onError(locationServer.buildException(Status.FAILED_PRECONDITION.getCode(), ex.getMessage(), -1, true));
		} catch (InvalidSignatureException ex2) {
			responseObserver.onError(locationServer.buildException(Status.PERMISSION_DENIED.getCode(), ex2.getMessage(), -1, true));
		} catch (GeneralSecurityException | IOException ex3) {
			responseObserver.onError(locationServer.buildException(Status.ABORTED.getCode(), ex3.getMessage(), -1, true));
		}
	}

    @Override
    public void requestMyProofs(LocationServer.RequestMyProofsRequest request, StreamObserver<LocationServer.RequestMyProofsResponse> responseObserver) {
        try {
            if (Context.current().isCancelled()) {
                System.out.println("TIMEOUT SERVER");
                responseObserver.onError(Status.CANCELLED.asRuntimeException());
                return;
            }
            LocationServer.RequestMyProofsResponse response = locationServer.requestMyProofs(request);

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (StaleException ex) {
			responseObserver.onError(locationServer.buildException(Status.FAILED_PRECONDITION.getCode(), ex.getMessage(), locationServer.getUserIdFromRequestUserProofsRequest(request), true));
        } catch (InvalidSignatureException ex2) {
			responseObserver.onError(locationServer.buildException(Status.PERMISSION_DENIED.getCode(), ex2.getMessage(), locationServer.getUserIdFromRequestUserProofsRequest(request), true));
        } catch (GeneralSecurityException | IOException ex3) {
			responseObserver.onError(locationServer.buildException(Status.ABORTED.getCode(), ex3.getMessage(), locationServer.getUserIdFromRequestUserProofsRequest(request), true));
        }
    }
}
