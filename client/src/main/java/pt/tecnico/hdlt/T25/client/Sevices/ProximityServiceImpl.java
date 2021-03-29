package pt.tecnico.hdlt.T25.client.Sevices;

import io.grpc.stub.StreamObserver;
import pt.tecnico.hdlt.T25.Proximity;
import pt.tecnico.hdlt.T25.ProximityServiceGrpc;

public class ProximityServiceImpl extends ProximityServiceGrpc.ProximityServiceImplBase {

    @Override
    public void proof(Proximity.LocationProofRequest request, StreamObserver<Proximity.LocationProofResponse> responseObserver) {

        String messageContent = "{message: \"Ola, Boa tarde\", nota: 20}";

        Proximity.LocationProofResponse response = Proximity.LocationProofResponse
                .newBuilder()
                .setContent(messageContent)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
