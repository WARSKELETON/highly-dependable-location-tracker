package pt.tecnico.hdlt.T25.client.Domain;

import java.util.List;
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
}
