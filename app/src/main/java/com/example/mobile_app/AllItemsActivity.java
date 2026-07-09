package com.example.mobile_app;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class AllItemsActivity extends AppCompatActivity {

    private RecyclerView rvAllItems;
    private TextView txtTitleAll;
    private FirebaseFirestore db;
    private String categoryType;

    // Variabile noi pentru a ști din ce oraș să luăm datele
    private String currentCountryId;
    private String currentCityId;

    private List<PoiRow> poiList;
    private PoiAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_items);

        db = FirebaseFirestore.getInstance();
        rvAllItems = findViewById(R.id.rvAllItems);
        txtTitleAll = findViewById(R.id.txtTitleAll);

        rvAllItems.setLayoutManager(new LinearLayoutManager(this));
        poiList = new ArrayList<>();

        adapter = new PoiAdapter(poiList);
        rvAllItems.setAdapter(adapter);

        findViewById(R.id.btnBackAll).setOnClickListener(v -> finish());

        categoryType = getIntent().getStringExtra("CATEGORY_TYPE");

        // Preluăm țara și orașul primite de la ecranul de detalii
        currentCountryId = getIntent().getStringExtra("countryId");
        currentCityId = getIntent().getStringExtra("cityId");

        // Fallback în caz că datele lipsesc dintr-un motiv oarecare
        if (currentCountryId == null) currentCountryId = "italy";
        if (currentCityId == null) currentCityId = "rome";

        if (categoryType != null) {
            if (categoryType.equals("attractions")) {
                txtTitleAll.setText("All Attractions");
            } else {
                txtTitleAll.setText("All Restaurants");
            }
            loadDataFromFirebase();
        }
    }

    private void loadDataFromFirebase() {
        // Înlocuim "italy" și "rome" cu variabilele dinamice primite!
        db.collection("countries").document(currentCountryId)
                .collection("cities").document(currentCityId)
                .collection(categoryType)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        poiList.clear();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            PoiRow row = null;

                            if (categoryType.equals("attractions")) {
                                Attraction attraction = document.toObject(Attraction.class);
                                if (attraction.getId() == null) {
                                    attraction.setId(document.getId());
                                }
                                row = PoiRow.fromAttraction(attraction);
                            } else {
                                Restaurant restaurant = document.toObject(Restaurant.class);
                                if (restaurant.getId() == null) {
                                    restaurant.setId(document.getId());
                                }
                                row = PoiRow.fromRestaurant(restaurant);
                            }

                            if (row != null) {
                                poiList.add(row);
                            }
                        }

                        adapter.notifyDataSetChanged();
                    }
                });
    }
}