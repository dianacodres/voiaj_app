package com.example.mobile_app;

import java.util.List;

public class City {

    private String id;
    private String name;
    private String countryId;
    private Double rating;
    private String tagline;
    private List<String> keywords;

    private String currency;
    private String language;
    private Long avgDayBudget;

    private Double lat;
    private Double lon;
    private String imageUrl;
    private String description;

    // Expresii scurte si specifice, DOAR pentru cautarea semantica AI (recomandari de destinatie) -
    // separat de "keywords", care raman legate de cele 6 categorii fixe de la onboarding.
    private List<String> semanticTags;

    public City() {
    }

    public City(String id, String name, Double lat, Double lon) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCountryId() { return countryId; }
    public void setCountryId(String countryId) { this.countryId = countryId; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public String getTagline() { return tagline; }
    public void setTagline(String tagline) { this.tagline = tagline; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Long getAvgDayBudget() { return avgDayBudget; }
    public void setAvgDayBudget(Long avgDayBudget) { this.avgDayBudget = avgDayBudget; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getSemanticTags() { return semanticTags; }
    public void setSemanticTags(List<String> semanticTags) { this.semanticTags = semanticTags; }
}