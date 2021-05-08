package pt.tecnico.hdlt.T25.server.Services;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import pt.tecnico.hdlt.T25.ByzantineReliableBroadcast;
import pt.tecnico.hdlt.T25.ByzantineReliableBroadcastServiceGrpc;
import pt.tecnico.hdlt.T25.server.Domain.Server;

import java.util.logging.Logger;

public class ByzantineReliableBroadcastServiceImpl extends ByzantineReliableBroadcastServiceGrpc.ByzantineReliableBroadcastServiceImplBase {
    private static final Logger LOGGER = Logger.getLogger(ByzantineReliableBroadcastServiceImpl.class.getName());
    private final Server locationServer;

    public ByzantineReliableBroadcastServiceImpl(Server server) {
        locationServer = server;
    }

    @Override
    public void echo(ByzantineReliableBroadcast.EchoRequest request, StreamObserver<Empty> responseObserver) {
        locationServer.handleEcho(request);

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void ready(ByzantineReliableBroadcast.ReadyRequest request, StreamObserver<Empty> responseObserver) {
        super.ready(request, responseObserver);
    }
}
