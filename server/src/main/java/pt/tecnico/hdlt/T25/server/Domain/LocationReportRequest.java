package pt.tecnico.hdlt.T25.server.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class LocationReportRequest extends Location {

    private int sourceClientId;

    public LocationReportRequest() {
    }

    public LocationReportRequest(int userId, int ep, int latitude, int longitude, int sourceClientId) {
        super(userId, ep, latitude, longitude);
        this.sourceClientId = sourceClientId;
    }

    public int getSourceClientId() {
        return sourceClientId;
    }

    public void setSourceClientId(int sourceClientId) {
        this.sourceClientId = sourceClientId;
    }

    @Override
    public ObjectNode toJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode node = objectMapper.createObjectNode();

        node.put("userId", this.getUserId());
        node.put("ep", this.getEp());
        node.put("latitude", this.getLatitude());
        node.put("longitude", this.getLongitude());
        node.put("sourceClientId", this.sourceClientId);

        return node;
    }

    @Override
    public String toJsonString() {
        return toJson().toString();
    }
}
