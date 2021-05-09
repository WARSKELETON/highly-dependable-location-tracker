package pt.tecnico.hdlt.T25.server.Services;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.tecnico.hdlt.T25.ByzantineReliableBroadcast;
import pt.tecnico.hdlt.T25.ByzantineReliableBroadcastServiceGrpc;
import pt.tecnico.hdlt.T25.server.Domain.Exceptions.InvalidSignatureException;
import pt.tecnico.hdlt.T25.server.Domain.Server;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.logging.Logger;

public class ByzantineReliableBroadcastServiceImpl extends ByzantineReliableBroadcastServiceGrpc.ByzantineReliableBroadcastServiceImplBase {
    private static final Logger LOGGER = Logger.getLogger(ByzantineReliableBroadcastServiceImpl.class.getName());
    private final Server locationServer;

    public ByzantineReliableBroadcastServiceImpl(Server server) {
        locationServer = server;
    }

    @Override
    public void echo(ByzantineReliableBroadcast.RequestMsg request, StreamObserver<Empty> responseObserver) {
        try {
            locationServer.handleEcho(request);

            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        } catch (InvalidSignatureException ex3) {
            responseObserver.onError(Status.UNAUTHENTICATED.withDescription(ex3.getMessage()).asRuntimeException());
        } catch (GeneralSecurityException | IOException ex4) {
            responseObserver.onError(Status.ABORTED.withDescription(ex4.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void ready(ByzantineReliableBroadcast.RequestMsg request, StreamObserver<Empty> responseObserver) {
        try {
            locationServer.handleReady(request);

            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();
        } catch (InvalidSignatureException ex3) {
            responseObserver.onError(Status.UNAUTHENTICATED.withDescription(ex3.getMessage()).asRuntimeException());
        } catch (GeneralSecurityException | IOException ex4) {
            responseObserver.onError(Status.ABORTED.withDescription(ex4.getMessage()).asRuntimeException());
        }
    }
}
