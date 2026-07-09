package com.example.mobile_app;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Citeste orasele din Firestore. Foloseste collectionGroup("cities"), care aduce
 * TOATE documentele din orice subcolectie "cities", indiferent sub ce tara sunt -
 * nu trebuie sa stii dinainte lista de tari.
 */
public class CityRepository {

    private final FirebaseFirestore firestore;

    public CityRepository() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    public interface CityListCallback {
        void onSuccess(List<City> cities);
        void onError(String errorMessage);
    }

    public void getAllCities(CityListCallback callback) {
        firestore.collectionGroup("cities")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<City> cities = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        cities.add(doc.toObject(City.class));
                    }
                    callback.onSuccess(cities);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * Cauta orasele care au un anumit keyword in array-ul "keywords" din Firestore.
     * Folosit la ecranul de moods (History, Sun & Beach, Food & Wine etc.).
     */
    public void getCitiesByKeyword(String keyword, CityListCallback callback) {
        firestore.collectionGroup("cities")
                .whereArrayContains("keywords", keyword)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<City> cities = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        cities.add(doc.toObject(City.class));
                    }
                    callback.onSuccess(cities);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /**
     * La fel ca getCitiesByKeyword, dar potriveste orice oras care are CEL PUTIN
     * unul dintre keyword-urile date (util pentru "Art & Museums" = art SAU museums).
     */
    public void getCitiesByAnyKeyword(List<String> keywords, CityListCallback callback) {
        firestore.collectionGroup("cities")
                .whereArrayContainsAny("keywords", keywords)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<City> cities = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        cities.add(doc.toObject(City.class));
                    }
                    callback.onSuccess(cities);
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }
}