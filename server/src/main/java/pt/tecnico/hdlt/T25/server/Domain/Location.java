package pt.tecnico.hdlt.T25.server.Domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Location {

    private int userId;
    private int ep;
    private int latitude;
    private int longitude;

    public Location() {
    }

    public Location(int userId, int ep, int latitude, int longitude) {
        this.userId = userId;
        this.ep = ep;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getEp() {
        return ep;
    }

    public void setEp(int ep) {
        this.ep = ep;
    }

    public int getLatitude() {
        return latitude;
    }

    public void setLatitude(int latitude) {
        this.latitude = latitude;
    }

    public int getLongitude() {
        return longitude;
    }

    public void setLongitude(int longitude) {
        this.longitude = longitude;
    }

    public ObjectNode toJson() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode node = objectMapper.createObjectNode();

        node.put("userId", userId);
        node.put("ep", ep);
        node.put("latitude", latitude);
        node.put("longitude", longitude);

        return node;
    }

    public String toJsonString() {
        return toJson().toString();
    }
}
