package com.example.mobile_app;

/** Model pentru un document din countries/{c}/cities/{c}/restaurants/{id}. */
public class Restaurant {

    private String id;
    private String name;
    private String imageUrl;
    private String neighborhood;
    private String zone;
    private String priceRange;
    private Double rating;
    private String category;
    private java.util.List<String> mealTime;
    private java.util.List<String> tags;
    private int mealOrder;
    private java.util.List<String> bestFor;
    private String address;

    public Restaurant() {
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

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getPriceRange() {
        return priceRange;
    }

    public void setPriceRange(String priceRange) {
        this.priceRange = priceRange;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public java.util.List<String> getMealTime() {
        return mealTime;
    }

    public void setMealTime(java.util.List<String> mealTime) {
        this.mealTime = mealTime;
    }

    public java.util.List<String> getTags() {
        return tags;
    }

    public void setTags(java.util.List<String> tags) {
        this.tags = tags;
    }

    public int getMealOrder() {
        return mealOrder;
    }

    public void setMealOrder(int mealOrder) {
        this.mealOrder = mealOrder;
    }

    public java.util.List<String> getBestFor() {
        return bestFor;
    }

    public void setBestFor(java.util.List<String> bestFor) {
        this.bestFor = bestFor;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}