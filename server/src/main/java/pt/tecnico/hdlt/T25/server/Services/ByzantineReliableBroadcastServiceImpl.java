package pt.tecnico.hdlt.T25.server.Services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.Empty;
import io.grpc.Context;
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
        Context ctx = Context.current().fork();
        ctx.run(() -> {
            try {
                locationServer.handleEcho(request);
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            } catch (InvalidSignatureException e) {
                e.printStackTrace();
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        });

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void ready(ByzantineReliableBroadcast.RequestMsg request, StreamObserver<Empty> responseObserver) {
        Context ctx = Context.current().fork();
        ctx.run(() -> {
            try {
                locationServer.handleReady(request);
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            } catch (InvalidSignatureException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }
}
