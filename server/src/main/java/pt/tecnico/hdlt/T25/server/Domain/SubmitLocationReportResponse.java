package pt.tecnico.hdlt.T25.server.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class SubmitLocationReportResponse {
    private int clientId;
    private int serverId;
    private boolean ok;

    public SubmitLocationReportResponse() {
    }

    public SubmitLocationReportResponse(int clientId, int serverId, boolean ok) {
        this.clientId = clientId;
        this.serverId = serverId;
        this.ok = ok;
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

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public ObjectNode toJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode node = objectMapper.createObjectNode();

        node.put("clientId", this.getClientId());
        node.put("serverId", this.getServerId());
        node.put("ok", this.isOk());

        return node;
    }

    public String toJsonString() {
        return toJson().toString();
    }
}
