package pt.tecnico.hdlt.T25.server.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class LocationReportRequest extends Location {

    private int sourceClientId;
    private int seqNumber;

    public LocationReportRequest() {
    }

    public LocationReportRequest(int userId, int ep, int latitude, int longitude, int sourceClientId, int seqNumber) {
        super(userId, ep, latitude, longitude);
        this.sourceClientId = sourceClientId;
        this.seqNumber = seqNumber;
    }

    public int getSourceClientId() {
        return sourceClientId;
    }

    public void setSourceClientId(int sourceClientId) {
        this.sourceClientId = sourceClientId;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public void setSeqNumber(int seqNumber) {
        this.seqNumber = seqNumber;
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
        node.put("seqNumber", this.seqNumber);

        return node;
    }

    @Override
    public String toJsonString() {
        return toJson().toString();
    }
}
