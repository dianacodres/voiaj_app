package com.example.mobile_app;

import com.example.mobile_app.ItineraryItem;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

/**
 * Model comun pentru documentele din:
 *   users/{uid}/completedTrips/{tripId}
 *   users/{uid}/savedTrips/{tripId}
 *
 * Campurile status, countdownActive, daysUntilTrip sunt relevante
 * doar pentru savedTrips - raman null/false pentru completedTrips.
 */
public class Trip {

    @DocumentId
    private String tripId;

    private String cityId;
    private String cityName;
    private String countryId;

    private String startDate; // format: "yyyy-MM-dd", ex "2024-05-14"
    private String endDate;
    private int daysCount;

    // --- specifice savedTrips ---
    private String status;          // "planned" sau "draft"
    private boolean countdownActive;
    private int daysUntilTrip;      // recalculat manual la fiecare update, vezi recalculateCountdown()

    @ServerTimestamp
    private Date createdAt;

    private java.util.List<ItineraryItem> itinerary;

    public Trip() {
    }

    // Constructor pentru completedTrips
    public static Trip createCompleted(String cityId, String cityName, String countryId,
                                       String startDate, String endDate, int daysCount) {
        Trip trip = new Trip();
        trip.cityId = cityId;
        trip.cityName = cityName;
        trip.countryId = countryId;
        trip.startDate = startDate;
        trip.endDate = endDate;
        trip.daysCount = daysCount;
        return trip;
    }

    // Constructor pentru savedTrips
    public static Trip createSaved(String cityId, String cityName, String countryId,
                                   String startDate, String endDate, int daysCount,
                                   String status) {
        Trip trip = createCompleted(cityId, cityName, countryId, startDate, endDate, daysCount);
        trip.status = status;
        trip.recalculateCountdown();
        return trip;
    }

    /**
     * Recalculeaza daysUntilTrip si countdownActive pe baza startDate.
     * Se apeleaza manual (in ViewModel/Adapter, la afisare), NU se bazeaza
     * pe o valoare stocata static in Firestore, ca sa nu ramana desincronizata.
     */
    @Exclude
    public void recalculateCountdown() {
        if (startDate == null) {
            this.countdownActive = false;
            this.daysUntilTrip = 0;
            return;
        }
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            Date start = sdf.parse(startDate);

            // Normalizam ambele date la miezul noptii, ca diferenta sa fie in zile
            // calendaristice intregi, nu in ore brute (evita sa arate "0 days" cu cateva
            // ore inainte de miezul noptii zilei premergatoare plecarii).
            java.util.Calendar startCal = java.util.Calendar.getInstance();
            startCal.setTime(start);
            startCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            startCal.set(java.util.Calendar.MINUTE, 0);
            startCal.set(java.util.Calendar.SECOND, 0);
            startCal.set(java.util.Calendar.MILLISECOND, 0);

            java.util.Calendar todayCal = java.util.Calendar.getInstance();
            todayCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            todayCal.set(java.util.Calendar.MINUTE, 0);
            todayCal.set(java.util.Calendar.SECOND, 0);
            todayCal.set(java.util.Calendar.MILLISECOND, 0);

            long diffMillis = startCal.getTimeInMillis() - todayCal.getTimeInMillis();
            int days = (int) (diffMillis / (1000 * 60 * 60 * 24));
            this.daysUntilTrip = Math.max(days, 0);
            this.countdownActive = days >= 0;
        } catch (java.text.ParseException e) {
            this.countdownActive = false;
            this.daysUntilTrip = 0;
        }
    }

    /** Verifica daca ESTE chiar acum in perioada calatoriei (intre startDate si endDate, inclusiv). */
    @Exclude
    public boolean isOngoing() {
        if (startDate == null || endDate == null) return false;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);

            java.util.Calendar startOfDay = java.util.Calendar.getInstance();
            startOfDay.setTime(sdf.parse(startDate));
            startOfDay.set(java.util.Calendar.HOUR_OF_DAY, 0);
            startOfDay.set(java.util.Calendar.MINUTE, 0);
            startOfDay.set(java.util.Calendar.SECOND, 0);

            long now = System.currentTimeMillis();
            return now >= startOfDay.getTimeInMillis() && !hasEnded();
        } catch (java.text.ParseException e) {
            return false;
        }
    }

    /**
     * Verifica daca a trecut complet ultima zi a calatoriei (endDate).
     * Diferit de countdownActive/daysUntilTrip, care doar arata "cat mai e pana pleci" -
     * asta raspunde la "s-a terminat efectiv vacanta?", folosit pentru mutarea automata
     * in completedTrips abia dupa ultima zi, nu de la prima zi de calatorie.
     */
    @Exclude
    public boolean hasEnded() {
        if (endDate == null) return false;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            Date end = sdf.parse(endDate);
            java.util.Calendar endOfDay = java.util.Calendar.getInstance();
            endOfDay.setTime(end);
            endOfDay.set(java.util.Calendar.HOUR_OF_DAY, 23);
            endOfDay.set(java.util.Calendar.MINUTE, 59);
            endOfDay.set(java.util.Calendar.SECOND, 59);
            return System.currentTimeMillis() > endOfDay.getTimeInMillis();
        } catch (java.text.ParseException e) {
            return false;
        }
    }

    // --- Getters & Setters ---

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getCityId() {
        return cityId;
    }

    public void setCityId(String cityId) {
        this.cityId = cityId;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public String getCountryId() {
        return countryId;
    }

    public void setCountryId(String countryId) {
        this.countryId = countryId;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public int getDaysCount() {
        return daysCount;
    }

    public void setDaysCount(int daysCount) {
        this.daysCount = daysCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isCountdownActive() {
        return countdownActive;
    }

    public void setCountdownActive(boolean countdownActive) {
        this.countdownActive = countdownActive;
    }

    public int getDaysUntilTrip() {
        return daysUntilTrip;
    }

    public void setDaysUntilTrip(int daysUntilTrip) {
        this.daysUntilTrip = daysUntilTrip;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public java.util.List<ItineraryItem> getItinerary() {
        return itinerary;
    }

    public void setItinerary(java.util.List<ItineraryItem> itinerary) {
        this.itinerary = itinerary;
    }
}