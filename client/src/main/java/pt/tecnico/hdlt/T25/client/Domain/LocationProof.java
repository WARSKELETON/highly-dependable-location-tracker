package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class LocationProof extends Location {

    private int witnessId;
    private int witnessLatitude;
    private int witnessLongitude;

    public LocationProof() {
    }

    LocationProof(int userId, int ep, int latitude, int longitude, int witnessId, int witnessLatitude, int witnessLongitude) {
        super(userId, ep, latitude, longitude);
        this.witnessId = witnessId;
        this.witnessLatitude = witnessLatitude;
        this.witnessLongitude = witnessLongitude;
    }

    public int getWitnessId() {
        return witnessId;
    }

    public void setWitnessId(int witnessId) {
        this.witnessId = witnessId;
    }

    public int getWitnessLatitude() {
        return witnessLatitude;
    }

    public void setWitnessLatitude(int witnessLatitude) {
        this.witnessLatitude = witnessLatitude;
    }

    public int getWitnessLongitude() {
        return witnessLongitude;
    }

    public void setWitnessLongitude(int witnessLongitude) {
        this.witnessLongitude = witnessLongitude;
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
