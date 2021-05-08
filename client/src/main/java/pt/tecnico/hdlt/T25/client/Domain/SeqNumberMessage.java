package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SeqNumberMessage {
    private int clientId;
    private int serverId;
    private int seqNumber;

    public SeqNumberMessage() {
    }

    public SeqNumberMessage(int clientId, int serverId, int seqNumber) {
        this.clientId = clientId;
        this.serverId = serverId;
        this.seqNumber = seqNumber;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public int getServerId() {
        return serverId;
    }

    public void setServerId(int serverId) {
        this.serverId = serverId;
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
        node.put("serverId", this.getServerId());
        node.put("seqNumber", this.getSeqNumber());

        return node;
    }

    public String toJsonString() {
        return toJson().toString();
    }
}
