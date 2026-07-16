package com.example.mobile_app;

import java.io.Serializable;

/**
 * O intrare din itinerariul unui trip: un loc (atractie sau restaurant) + ziua
 * in care e planuit sa fie vizitat. Stocat ca array de obiecte pe Trip.itinerary
 * in Firestore (users/{uid}/savedTrips/{tripId}/itinerary).
 * Serializable - ca sa poata fi trimis prin Intent (preview inainte de salvare).
 */
public class ItineraryItem implements Serializable {

    private String poiId;
    private String type; // "attraction" sau "restaurant"
    private int day;      // 1, 2, 3... - ziua din calatorie in care e planuita vizita

    public ItineraryItem() {
    }

    public ItineraryItem(String poiId, String type, int day) {
        this.poiId = poiId;
        this.type = type;
        this.day = day;
    }

    public String getPoiId() {
        return poiId;
    }

    public void setPoiId(String poiId) {
        this.poiId = poiId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getDay() {
        return day;
    }

    public void setDay(int day) {
        this.day = day;
    }
}