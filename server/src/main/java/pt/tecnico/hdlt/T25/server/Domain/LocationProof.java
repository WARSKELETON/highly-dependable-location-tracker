package pt.tecnico.hdlt.T25.server.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class LocationProof extends Location {

    private int witnessId;

    public LocationProof() {
    }

    LocationProof(int userId, int ep, int latitude, int longitude, int witnessId) {
        super(userId, ep, latitude, longitude);
        this.witnessId = witnessId;
    }

    public int getWitnessId() {
        return witnessId;
    }

    public void setWitnessId(int witnessId) {
        this.witnessId = witnessId;
    }

    @Override
    public ObjectNode toJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode node = objectMapper.createObjectNode();

        node.put("userId", this.getUserId());
        node.put("ep", this.getEp());
        node.put("latitude", this.getLatitude());
        node.put("longitude", this.getLongitude());
        node.put("witnessId", this.witnessId);

        return node;
    }

    @Override
    public String toJsonString() {
        return toJson().toString();
    }
}
