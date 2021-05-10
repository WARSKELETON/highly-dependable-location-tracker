package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SubmitLocationReportRequestHeader {
    private int clientId;
    private int serverId;
    private String proofOfWork;
    private int counter;

    public SubmitLocationReportRequestHeader() {
    }

    public SubmitLocationReportRequestHeader(int clientId, int serverId, String proofOfWork, int counter) {
        this.clientId = clientId;
        this.serverId = serverId;
        this.proofOfWork = proofOfWork;
        this.counter = counter;
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

    public String getProofOfWork() {
        return proofOfWork;
    }

    public void setProofOfWork(String proofOfWork) {
        this.proofOfWork = proofOfWork;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public ObjectNode toJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode node = objectMapper.createObjectNode();

        node.put("clientId", this.getClientId());
        node.put("serverId", this.getServerId());
        node.put("proofOfWork", this.getProofOfWork());
        node.put("counter", this.getCounter());

        return node;
    }

    public String toJsonString() {
        return toJson().toString();
    }
}
