package com.example.mobile_app;

/** Model pentru un document din countries/{c}/cities/{c}/attractions/{id}. */
public class Attraction {

    private String id;
    private String name;
    private String imageUrl;
    private String duration;
    private double ticketPrice;
    private String category;
    private String zone;
    private int routeOrder;
    private int visitDurationMinutes;
    private String bestTimeOfDay;
    private boolean bookingRequired;
    private String ticketUrl; // optional - nu toate atractiile au link de bilete
    private String infoUrl;   // pentru cele gratuite - link informativ oficial, nu de bilete
    private java.util.List<String> tags;

    public Attraction() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public double getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(double ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public int getRouteOrder() {
        return routeOrder;
    }

    public void setRouteOrder(int routeOrder) {
        this.routeOrder = routeOrder;
    }

    public int getVisitDurationMinutes() {
        return visitDurationMinutes;
    }

    public void setVisitDurationMinutes(int visitDurationMinutes) {
        this.visitDurationMinutes = visitDurationMinutes;
    }

    public String getBestTimeOfDay() {
        return bestTimeOfDay;
    }

    public void setBestTimeOfDay(String bestTimeOfDay) {
        this.bestTimeOfDay = bestTimeOfDay;
    }

    public boolean isBookingRequired() {
        return bookingRequired;
    }

    public void setBookingRequired(boolean bookingRequired) {
        this.bookingRequired = bookingRequired;
    }

    public String getTicketUrl() {
        return ticketUrl;
    }

    public void setTicketUrl(String ticketUrl) {
        this.ticketUrl = ticketUrl;
    }

    public String getInfoUrl() {
        return infoUrl;
    }

    public void setInfoUrl(String infoUrl) {
        this.infoUrl = infoUrl;
    }

    /** true daca intrarea e gratuita (ticketPrice == 0) - folosit de algoritmul de itinerariu/AI. */
    public boolean isFree() {
        return ticketPrice <= 0;
    }

    public java.util.List<String> getTags() {
        return tags;
    }

    public void setTags(java.util.List<String> tags) {
        this.tags = tags;
    }
}