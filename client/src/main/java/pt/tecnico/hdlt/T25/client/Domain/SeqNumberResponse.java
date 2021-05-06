package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SeqNumberResponse {
    private int clientId;
    private int seqNumber;

    public SeqNumberResponse() {
    }

    public SeqNumberResponse(int clientId, int seqNumber) {
        this.clientId = clientId;
        this.seqNumber = seqNumber;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public void setSeqNumber(int seqNumber) {
        this.seqNumber = seqNumber;
    }

    public ObjectNode toJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode node = objectMapper.createObjectNode();

        node.put("clientId", this.getClientId());
        node.put("seqNumber", this.getSeqNumber());

        return node;
    }

    public String toJsonString() {
        return toJson().toString();
    }
}
