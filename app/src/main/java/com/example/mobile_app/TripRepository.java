package com.example.mobile_app;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gestioneaza scrierea si citirea din:
 *   users/{uid}/completedTrips/{tripId}
 *   users/{uid}/savedTrips/{tripId}
 */
@SuppressWarnings({"unused", "SpellCheckingInspection"})
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

    public interface ChatHistoryCallback {
        void onSuccess(List<Map<String, Object>> intrari);
        void onError(String errorMessage);
    }

    /**
     * Adauga un trip in savedTrips si incrementeaza atomic savedTripsCount pe users/{uid}.
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
     * Actualizeaza un trip existent in savedTrips.
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
                        trip.setTripId(doc.getId());
                        trip.recalculateCountdown();
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
                        Trip trip = doc.toObject(Trip.class);
                        trip.setTripId(doc.getId());
                        trips.add(trip);
                    }
                    callback.onSuccess(trips);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Cauta urmatoarea calatorie planificata ("planned") din savedTrips a carei data
     * de incheiere este mai mare sau egala cu data curenta.
     */
    public void getNextUpcomingTrip(String userId, TripCallback callback) {
        String todayStr = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        firestore.collection("users").document(userId).collection("savedTrips")
                .whereEqualTo("status", "planned")
                .whereGreaterThanOrEqualTo("endDate", todayStr)
                .orderBy("endDate", Query.Direction.ASCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                        DocumentSnapshot doc = queryDocumentSnapshots.getDocuments().get(0);
                        Trip trip = doc.toObject(Trip.class);
                        if (trip != null) {
                            trip.setTripId(doc.getId());
                            trip.recalculateCountdown();
                        }
                        callback.onSuccess(trip);
                    } else {
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage() != null ? e.getMessage() : "Eroare la preluarea urmatoarei calatorii"));
    }

    /**
     * Cauta printre savedTrips cele cu status "planned" care s-au terminat efectiv
     * si le muta automat in completedTrips.
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

    /** Sterge un trip din savedTrips si decrementeaza contorul. */
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

    /** Promoveaza un draft la "planned". */
    public void promoteDraftToPlanned(String uid, String tripId, String startDate, String endDate,
                                      int daysCount, SimpleCallback callback) {
        Map<String, Object> updates = new HashMap<>();
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

    /** Muta un trip din savedTrips in completedTrips si actualizeaza ambele contoare. */
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

    /** Salveaza o intrare in istoric la salvat itinerariu AI. */
    public void logChatHistoryEntry(String uid, String cityName, String tripId, int daysCount, SimpleCallback callback) {
        Map<String, Object> intrare = new HashMap<>();
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

    /** Preia ultimele N intrari din istoric. */
    public void getRecentChatHistory(String uid, int limit, ChatHistoryCallback callback) {
        firestore.collection("users").document(uid)
                .collection("chatHistory")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Map<String, Object>> intrari = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        intrari.add(doc.getData());
                    }
                    callback.onSuccess(intrari);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}