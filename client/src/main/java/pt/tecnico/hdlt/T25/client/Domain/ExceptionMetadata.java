package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ExceptionMetadata {

    private String statusCode;
    private String description;
    private int userId;
    private int serverId;
    private int seqNumber;

    public ExceptionMetadata() {
    }

    public ExceptionMetadata(String statusCode, String description, int userId, int serverId, int seqNumber) {
        this.statusCode = statusCode;
        this.description = description;
        this.userId = userId;
        this.serverId = serverId;
        this.seqNumber = seqNumber;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
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

        node.put("statusCode", statusCode);
        node.put("description", description);
        node.put("userId", userId);
        node.put("serverId", serverId);
        node.put("seqNumber", seqNumber);

        return node;
    }

    public String toJsonString() {
        return toJson().toString();
    }
}
