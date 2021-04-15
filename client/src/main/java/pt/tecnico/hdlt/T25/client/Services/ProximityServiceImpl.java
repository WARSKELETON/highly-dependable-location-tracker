package pt.tecnico.hdlt.T25.client.Services;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.tecnico.hdlt.T25.Proximity;
import pt.tecnico.hdlt.T25.ProximityServiceGrpc;
import pt.tecnico.hdlt.T25.client.Domain.Client;

import java.io.IOException;
import java.util.logging.Logger;

public class ProximityServiceImpl extends ProximityServiceGrpc.ProximityServiceImplBase {

    private static final Logger LOGGER = Logger.getLogger(ProximityServiceImpl.class.getName());
    private final Client client;

    public ProximityServiceImpl(Client client) {
        this.client = client;
    }

    @Override
    public void requestLocationProof(Proximity.LocationProofRequest request, StreamObserver<Proximity.LocationProofResponse> responseObserver) {
        try {
            if (Context.current().isCancelled()) {
                System.out.println("TIMEOUT CLIENT");
                responseObserver.onError(Status.CANCELLED.asRuntimeException());
                return;
            }

            if (client.verifyLocationProofRequest(request)) {
                System.out.println("user" + client.getClientId() + ": Location proof request verified.");

                Proximity.LocationProofResponse response = client.buildLocationProof(request);

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        } catch (IOException e) {
            LOGGER.info(e.getMessage());
            responseObserver.onError(Status.ABORTED.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
