package pt.tecnico.hdlt.T25.client.Domain;

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
}
