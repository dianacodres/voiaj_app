package com.example.mobile_app;

import java.util.List;

public class City {

    private String id;
    private String name;
    private String countryId;
    private Double rating;
    private String tagline;
    private List<String> keywords;

    // CÂMPURILE NOI PENTRU CARDUL DE DETALII:
    private String currency;
    private String language;
    private Long avgDayBudget; // Folosim Long sau Double în funcție de cum e salvat numărul în Firestore

    public City() {
    }

    // Getter-ii și Setter-ii tăi existenți rămân neschimbați...
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

    // GETTERI ȘI SETTERI PENTRU CÂMPURILE NOI:
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Long getAvgDayBudget() { return avgDayBudget; }
    public void setAvgDayBudget(Long avgDayBudget) { this.avgDayBudget = avgDayBudget; }
}