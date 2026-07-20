package com.example.mobile_app;

/**
 * Reprezintă o atracție/local salvat pentru un anumit oraș, cu ziua din traseu
 * căreia îi aparține (pentru draft-uri, toate au ziua 1 — "All days").
 */
public class RoutePlace {

    private String id;
    private String name;
    private String category;
    private String imageUrl;
    private double lat;
    private double lon;
    private int dayNumber;

    public RoutePlace(String id, String name, String category, String imageUrl,
                      double lat, double lon, int dayNumber) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.imageUrl = imageUrl;
        this.lat = lat;
        this.lon = lon;
        this.dayNumber = dayNumber;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getImageUrl() { return imageUrl; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public int getDayNumber() { return dayNumber; }
    public void setDayNumber(int dayNumber) { this.dayNumber = dayNumber; }
}
