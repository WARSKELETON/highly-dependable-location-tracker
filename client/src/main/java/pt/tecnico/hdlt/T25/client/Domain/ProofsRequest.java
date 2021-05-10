package pt.tecnico.hdlt.T25.client.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;

public class ProofsRequest {

    private int userId;
    private ArrayList<Integer> eps;
    private int seqNumber;
    private int serverId;

    public ProofsRequest() {
    }

    public ProofsRequest(int userId, ArrayList<Integer> eps, int seqNumber, int serverId) {
        this.userId = userId;
        this.eps = eps;
        this.seqNumber = seqNumber;
        this.serverId = serverId;
    }

    public int getUserId() {
        return userId;
    }

    public ArrayList<Integer> getEps() {
        return eps;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public int getServerId() {
        return serverId;
    }

    public ObjectNode toJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode node = objectMapper.createObjectNode();

        node.put("userId", userId);
        node.put("seqNumber", seqNumber);
        node.put("serverId", serverId);
        node.set("eps", objectMapper.valueToTree(eps));

        return node;
    }

    public String toJsonString() {
        return toJson().toString();
    }
}
