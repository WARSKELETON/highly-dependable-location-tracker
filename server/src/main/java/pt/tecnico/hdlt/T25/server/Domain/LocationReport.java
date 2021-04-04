package pt.tecnico.hdlt.T25.server.Domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

public class LocationReport {
    private Location locationProver;
    private String locationProverSignature;
    private Map<Integer, String> locationProofsContent;
    private Map<Integer, String> locationProofsSignature;

    public LocationReport() {
    }

    public LocationReport(Location locationProver, String locationProverSignature, Map<Integer, String> locationProofsContent, Map<Integer, String> locationProofsSignature) {
        this.locationProver = locationProver;
        this.locationProverSignature = locationProverSignature;
        this.locationProofsContent = locationProofsContent;
        this.locationProofsSignature = locationProofsSignature;
    }

    public Location getLocationProver() {
        return locationProver;
    }

    public void setLocationProver(Location locationProver) {
        this.locationProver = locationProver;
    }

    public String getLocationProverSignature() {
        return locationProverSignature;
    }

    public void setLocationProverSignature(String locationProverSignature) {
        this.locationProverSignature = locationProverSignature;
    }

    public Map<Integer, String> getLocationProofsContent() {
        return locationProofsContent;
    }

    public void setLocationProofsContent(Map<Integer, String> locationProofsContent) {
        this.locationProofsContent = locationProofsContent;
    }

    public Map<Integer, String> getLocationProofsSignature() {
        return locationProofsSignature;
    }

    public void setLocationProofsSignature(Map<Integer, String> locationProofsSignature) {
        this.locationProofsSignature = locationProofsSignature;
    }

    public ObjectNode toJson() throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode node = objectMapper.createObjectNode();

        node.set("locationProver", this.locationProver.toJson());
        node.put("locationProverSignature", this.locationProverSignature);
        node.set("locationProofsContent", objectMapper.convertValue(locationProofsContent, JsonNode.class));
        node.set("locationProofsSignature", objectMapper.convertValue(locationProofsSignature, JsonNode.class));

        return node;
    }

    public String toJsonString() throws JsonProcessingException {
        return toJson().toString();
    }
}
