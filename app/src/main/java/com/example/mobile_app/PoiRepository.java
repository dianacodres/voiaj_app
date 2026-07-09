package com.example.mobile_app;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/** Citeste subcolectiile attractions/restaurants ale unui oras specific (nu collectionGroup). */
public class PoiRepository {

    private final FirebaseFirestore firestore;

    public PoiRepository() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    public interface AttractionListCallback {
        void onSuccess(List<Attraction> attractions);
        void onError(String errorMessage);
    }

    public interface RestaurantListCallback {
        void onSuccess(List<Restaurant> restaurants);
        void onError(String errorMessage);
    }

    public void getAttractions(String countryId, String cityId, AttractionListCallback callback) {
        firestore.collection("countries").document(countryId)
                .collection("cities").document(cityId)
                .collection("attractions")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Attraction> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Attraction a = doc.toObject(Attraction.class);
                        if (a.getId() == null || a.getId().isEmpty()) {
                            a.setId(doc.getId()); // fallback: id-ul documentului, daca nu e stocat ca field
                        }
                        list.add(a);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void getRestaurants(String countryId, String cityId, RestaurantListCallback callback) {
        firestore.collection("countries").document(countryId)
                .collection("cities").document(cityId)
                .collection("restaurants")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Restaurant> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        Restaurant r = doc.toObject(Restaurant.class);
                        if (r.getId() == null || r.getId().isEmpty()) {
                            r.setId(doc.getId());
                        }
                        list.add(r);
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}