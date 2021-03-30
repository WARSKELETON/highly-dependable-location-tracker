package pt.tecnico.hdlt.T25.client.Domain;

import java.util.List;
import java.util.Map;

public class LocationReport {
    private Location locationProver;
    private Map<Integer, String> locationProofsContent;
    private Map<Integer, String> locationProofsSignature;

    public LocationReport() {
    }

    public LocationReport(Location locationProver, Map<Integer, String> locationProofsContent, Map<Integer, String> locationProofsSignature) {
        this.locationProver = locationProver;
        this.locationProofsContent = locationProofsContent;
        this.locationProofsSignature = locationProofsSignature;
    }

    public Location getLocationProver() {
        return locationProver;
    }

    public void setLocationProver(Location locationProver) {
        this.locationProver = locationProver;
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
