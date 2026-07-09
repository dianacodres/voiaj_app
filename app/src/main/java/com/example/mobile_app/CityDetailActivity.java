package com.example.mobile_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.FirebaseFirestore;

public class CityDetailActivity extends AppCompatActivity {

    private FirebaseFirestore db;

    private TextView txtCityName, txtCountryLabel, txtCityRating, txtCityTagline;
    private TextView txtCurrency, txtLanguage, txtAvgDay;


    private TextView txtGenerateItineraryTitle,txtGenerateItinerarySub;

    private String currentCityId;
    private String currentCountryId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_city_detail);

        db = FirebaseFirestore.getInstance();

        currentCityId = getIntent().getStringExtra("cityId");
        currentCountryId = getIntent().getStringExtra("countryId");

        if (currentCityId == null) currentCityId = "rome";
        if (currentCountryId == null) currentCountryId = "italy";

        // Legăm elementele vechi
        txtCityName = findViewById(R.id.txtCityName);
        txtCountryLabel = findViewById(R.id.txtCountryLabel);
        txtCityRating = findViewById(R.id.txtCityRating);
        txtCityTagline = findViewById(R.id.txtCityTagline);
        txtCurrency = findViewById(R.id.txtCurrency);
        txtLanguage = findViewById(R.id.txtLanguage);
        txtAvgDay = findViewById(R.id.txtAvgDay);

        // --- LINII NOI: Inițializăm elementele din cardul AI ---
        txtGenerateItineraryTitle = findViewById(R.id.txtGenerateItineraryTitle);
        txtGenerateItinerarySub = findViewById(R.id.txtGenerateItinerarySub);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.cityDetailRoot), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    findViewById(R.id.appBarLayout).setPadding(0, systemBars.top, 0, 0);
                    v.setPadding(0, 0, 0, systemBars.bottom);
                    return insets;
                });

        loadCityDetails();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnPlanTrip).setOnClickListener(v -> {
            startActivity(new Intent(CityDetailActivity.this, PlanTripActivity.class));
        });

        findViewById(R.id.tvViewAllAttractions).setOnClickListener(v -> {
            Intent intent = new Intent(CityDetailActivity.this, AllItemsActivity.class);
            intent.putExtra("CATEGORY_TYPE", "attractions");
            intent.putExtra("countryId", currentCountryId);
            intent.putExtra("cityId", currentCityId);
            startActivity(intent);
        });

        findViewById(R.id.tvViewAllRestaurants).setOnClickListener(v -> {
            Intent intent = new Intent(CityDetailActivity.this, AllItemsActivity.class);
            intent.putExtra("CATEGORY_TYPE", "restaurants");
            intent.putExtra("countryId", currentCountryId);
            intent.putExtra("cityId", currentCityId);
            startActivity(intent);
        });
    }

    private void loadCityDetails() {
        db.collection("countries").document(currentCountryId)
                .collection("cities").document(currentCityId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        City city = task.getResult().toObject(City.class);

                        if (city != null) {
                            if (city.getName() != null && txtCityName != null) {
                                txtCityName.setText(city.getName());

                                // --- LINIE NOUĂ: Actualizăm titlul cardului AI în mod dinamic! ---
                                if (txtGenerateItineraryTitle != null) {
                                    txtGenerateItineraryTitle.setText("Generate a " + city.getName() + " itinerary");
                                }

                                // ADAUGĂ ACEASTĂ LINIE: Acum variabila va fi folosită și va deveni mov!
                                if (txtGenerateItinerarySub != null) {
                                    txtGenerateItinerarySub.setText("Let the AI build your perfect days in " + city.getName());
                                }
                            }

                            if (city.getCountryId() != null && txtCountryLabel != null) {
                                String countryFormatted = city.getCountryId().substring(0, 1).toUpperCase()
                                        + city.getCountryId().substring(1);
                                txtCountryLabel.setText(countryFormatted);
                            }

                            if (city.getRating() != null && txtCityRating != null) {
                                txtCityRating.setText("☆ " + city.getRating());
                            }

                            if (city.getTagline() != null && txtCityTagline != null) {
                                txtCityTagline.setText("· " + city.getTagline());
                            }

                            if (city.getCurrency() != null && txtCurrency != null) {
                                txtCurrency.setText(city.getCurrency());
                            }

                            if (city.getLanguage() != null && txtLanguage != null) {
                                txtLanguage.setText(city.getLanguage());
                            }

                            if (city.getAvgDayBudget() != null && txtAvgDay != null) {
                                txtAvgDay.setText("~€" + city.getAvgDayBudget());
                            }
                        }
                    } else {
                        Toast.makeText(this, "Eroare la încărcarea datelor orașului", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}