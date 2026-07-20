package com.example.mobile_app;

import android.os.Bundle;
import android.view.View;
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
    private TextView txtNoItemsAll;
    private FirebaseFirestore db;
    private String categoryType;

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
        txtNoItemsAll = findViewById(R.id.txtNoItemsAll);

        rvAllItems.setLayoutManager(new LinearLayoutManager(this));
        poiList = new ArrayList<>();

        adapter = new PoiAdapter(poiList);
        rvAllItems.setAdapter(adapter);

        findViewById(R.id.btnBackAll).setOnClickListener(v -> finish());

        categoryType = getIntent().getStringExtra("CATEGORY_TYPE");
        currentCountryId = getIntent().getStringExtra("countryId");
        currentCityId = getIntent().getStringExtra("cityId");

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
        db.collection("countries").document(currentCountryId)
                .collection("cities").document(currentCityId)
                .collection(categoryType)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        poiList.clear();

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            PoiRow row;

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

                            poiList.add(row);
                        }

                        if (poiList.isEmpty()) {
                            rvAllItems.setVisibility(View.GONE);
                            if (txtNoItemsAll != null) {
                                txtNoItemsAll.setVisibility(View.VISIBLE);
                                if (categoryType.equals("attractions")) {
                                    txtNoItemsAll.setText("We couldn't find any attractions!");
                                } else {
                                    txtNoItemsAll.setText("We couldn't find any restaurants!");
                                }
                            }
                        } else {
                            rvAllItems.setVisibility(View.VISIBLE);
                            if (txtNoItemsAll != null) {
                                txtNoItemsAll.setVisibility(View.GONE);
                            }
                        }

                        adapter.notifyItemRangeChanged(0, poiList.size());
                    } else {
                        rvAllItems.setVisibility(View.GONE);
                        if (txtNoItemsAll != null) {
                            txtNoItemsAll.setVisibility(View.VISIBLE);
                            txtNoItemsAll.setText(categoryType.equals("attractions") ?
                                    "We couldn't find any attractions!" : "We couldn't find any restaurants!");
                        }
                    }
                });
    }
}