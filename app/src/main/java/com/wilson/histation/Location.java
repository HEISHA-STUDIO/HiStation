package com.wilson.histation;

public class Location {
    private float latitude;
    private float longitude;
    private float altitude;

    public Location() {}
    public Location(float lat, float lon, float alt) {
        this.latitude = lat;
        this.longitude = lon;
        this.altitude = alt;
    }

    public float getLatitude() {
        return this.latitude;
    }
    public void setLatitude(float lat) {
        this.latitude = lat;
    }

    public float getLongitude() {
        return this.longitude;
    }
    public void setLongitude(float lon) {
        this.longitude = lon;
    }

    public float getAltitude() {
        return this.altitude;
    }
    public void setAltitude(float alt) {
        this.altitude = alt;
    }
}
