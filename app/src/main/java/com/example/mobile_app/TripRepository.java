package com.example.mobile_app;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestioneaza scrierea si citirea din:
 *   users/{uid}/completedTrips/{tripId}
 *   users/{uid}/savedTrips/{tripId}
 * Separat de AuthRepository, ca sa nu amestecam logica de autentificare cu cea de trips.
 */
public class TripRepository {

    private final FirebaseFirestore firestore;

    public TripRepository() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    public interface TripCallback {
        void onSuccess(Trip trip);
        void onError(String errorMessage);
    }

    public interface TripListCallback {
        void onSuccess(List<Trip> trips);
        void onError(String errorMessage);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    /**
     * Adauga un trip in savedTrips si incrementeaza atomic savedTripsCount pe users/{uid}.
     * Folosit din PlanTripActivity - atat pentru "Plan with AI" (status "planned")
     * cat si pentru "Save as draft" (status "draft").
     */
    public void addSavedTrip(String uid, Trip trip, TripCallback callback) {
        firestore.collection("users").document(uid)
                .collection("savedTrips")
                .add(trip)
                .addOnSuccessListener(docRef -> {
                    trip.setTripId(docRef.getId());
                    firestore.collection("users").document(uid)
                            .update("savedTripsCount", FieldValue.increment(1));
                    callback.onSuccess(trip);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Adauga un trip in completedTrips si incrementeaza atomic completedTripsCount.
     * Folosit atunci cand un trip planuit se incheie (countdown ajuns la 0) sau
     * cand userul marcheaza manual o calatorie ca terminata.
     */
    public void addCompletedTrip(String uid, Trip trip, TripCallback callback) {
        firestore.collection("users").document(uid)
                .collection("completedTrips")
                .add(trip)
                .addOnSuccessListener(docRef -> {
                    trip.setTripId(docRef.getId());
                    firestore.collection("users").document(uid)
                            .update("completedTripsCount", FieldValue.increment(1));
                    callback.onSuccess(trip);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Actualizeaza un trip existent (folosit cand editezi un draft deja salvat,
     * in loc sa creezi un document nou - evita duplicatele).
     */
    public void updateSavedTrip(String uid, String tripId, Trip trip, TripCallback callback) {
        firestore.collection("users").document(uid)
                .collection("savedTrips").document(tripId)
                .set(trip)
                .addOnSuccessListener(unused -> {
                    trip.setTripId(tripId);
                    callback.onSuccess(trip);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /** Citeste toate savedTrips ale userului, cele mai noi primele. */
    public void getSavedTrips(String uid, TripListCallback callback) {
        firestore.collection("users").document(uid)
                .collection("savedTrips")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Trip> trips = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Trip trip = doc.toObject(Trip.class);
                        trip.recalculateCountdown(); // countdown mereu proaspat, nu din valoarea stocata
                        trips.add(trip);
                    }
                    callback.onSuccess(trips);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /** Citeste toate completedTrips ale userului, cele mai noi primele. */
    public void getCompletedTrips(String uid, TripListCallback callback) {
        firestore.collection("users").document(uid)
                .collection("completedTrips")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Trip> trips = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        trips.add(doc.toObject(Trip.class));
                    }
                    callback.onSuccess(trips);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Cauta printre savedTrips cele cu status "planned" care s-au terminat efectiv
     * (a trecut complet ultima zi - trip.hasEnded()) si le muta automat in completedTrips.
     * Se apeleaza inainte de a incarca listele, ca datele sa fie mereu corecte.
     */
    public void autoCompleteExpiredTrips(String uid, SimpleCallback callback) {
        getSavedTrips(uid, new TripListCallback() {
            @Override
            public void onSuccess(List<Trip> savedTrips) {
                List<Trip> expired = new ArrayList<>();
                for (Trip trip : savedTrips) {
                    if ("planned".equals(trip.getStatus()) && trip.hasEnded()) {
                        expired.add(trip);
                    }
                }

                if (expired.isEmpty()) {
                    callback.onSuccess();
                    return;
                }

                moveNextExpired(uid, expired, 0, callback);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    /** Muta trips-urile expirate unul cate unul. */
    private void moveNextExpired(String uid, List<Trip> expired, int index, SimpleCallback callback) {
        if (index >= expired.size()) {
            callback.onSuccess();
            return;
        }

        moveSavedToCompleted(uid, expired.get(index), new SimpleCallback() {
            @Override
            public void onSuccess() {
                moveNextExpired(uid, expired, index + 1, callback);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    /** Sterge un trip din savedTrips (folosit la "Delete" pe un draft) si decrementeaza contorul. */
    public void deleteSavedTrip(String uid, String tripId, SimpleCallback callback) {
        firestore.collection("users").document(uid)
                .collection("savedTrips").document(tripId)
                .delete()
                .addOnSuccessListener(unused -> {
                    firestore.collection("users").document(uid)
                            .update("savedTripsCount", FieldValue.increment(-1));
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Transforma un draft in "planned" (il "adopti" ca destinatie ferma).
     * Daca startDate/endDate sunt null (draft flexibil), le poti trimite acum
     * ca sa porneasca countdown-ul; daca deja are date, trimite null si ramane cum era.
     */
    public void promoteDraftToPlanned(String uid, String tripId, String startDate, String endDate,
                                      int daysCount, SimpleCallback callback) {
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("status", "planned");
        if (startDate != null) updates.put("startDate", startDate);
        if (endDate != null) updates.put("endDate", endDate);
        if (startDate != null) updates.put("daysCount", daysCount);

        firestore.collection("users").document(uid)
                .collection("savedTrips").document(tripId)
                .update(updates)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Muta un trip din savedTrips in completedTrips (cand se termina calatoria planuita)
     * si actualizeaza atomic ambele contoare.
     */
    public void moveSavedToCompleted(String uid, Trip trip, SimpleCallback callback) {
        String oldTripId = trip.getTripId();

        firestore.collection("users").document(uid)
                .collection("completedTrips")
                .add(trip)
                .addOnSuccessListener(docRef -> firestore.collection("users").document(uid)
                        .collection("savedTrips").document(oldTripId)
                        .delete()
                        .addOnSuccessListener(unused -> {
                            firestore.collection("users").document(uid)
                                    .update("completedTripsCount", FieldValue.increment(1),
                                            "savedTripsCount", FieldValue.increment(-1));
                            callback.onSuccess();
                        })
                        .addOnFailureListener(e -> callback.onError(e.getMessage())))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ── Istoric de conversatii AI - DOAR pentru itinerarii salvate, nu vreme/buget ──

    public interface ChatHistoryCallback {
        void onSuccess(java.util.List<java.util.Map<String, Object>> intrari);
        void onError(String errorMessage);
    }

    /** Salveaza o intrare in istoric, la momentul in care un itinerariu generat de AI e efectiv salvat. */
    public void logChatHistoryEntry(String uid, String cityName, String tripId, int daysCount, SimpleCallback callback) {
        java.util.Map<String, Object> intrare = new java.util.HashMap<>();
        intrare.put("cityName", cityName);
        intrare.put("tripId", tripId);
        intrare.put("daysCount", daysCount);
        intrare.put("createdAt", FieldValue.serverTimestamp());

        firestore.collection("users").document(uid)
                .collection("chatHistory")
                .add(intrare)
                .addOnSuccessListener(docRef -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /** Ia ultimele N intrari din istoric, cele mai noi primele - folosit la "Recent" in AI Hub. */
    public void getRecentChatHistory(String uid, int limit, ChatHistoryCallback callback) {
        firestore.collection("users").document(uid)
                .collection("chatHistory")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    java.util.List<java.util.Map<String, Object>> intrari = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        intrari.add(doc.getData());
                    }
                    callback.onSuccess(intrari);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}