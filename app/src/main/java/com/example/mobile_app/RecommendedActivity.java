package com.example.mobile_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

/** Ecranul complet de recomandari ("See all"), sortat descrescator dupa % match. */
public class RecommendedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_recommended);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.recommendedRoot), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(0, systemBars.top, 0, systemBars.bottom);
                    return insets;
                });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadRecommendations();
    }

    private void loadRecommendations() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        AuthRepository authRepository = new AuthRepository();
        authRepository.getCurrentUserProfile(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(User user) {
                CityRepository cityRepository = new CityRepository();
                cityRepository.getAllCities(new CityRepository.CityListCallback() {
                    @Override
                    public void onSuccess(List<City> cities) {
                        List<RecommendationEngine.CityMatch> ranked =
                                RecommendationEngine.rankCities(user.getInterests(), cities);

                        RecyclerView recyclerView = findViewById(R.id.recyclerRecommended);
                        recyclerView.setLayoutManager(new LinearLayoutManager(RecommendedActivity.this));
                        recyclerView.setAdapter(new RecommendedCityAdapter(ranked, city -> {
                            Intent intent = new Intent(RecommendedActivity.this, CityDetailActivity.class);
                            intent.putExtra("cityId", city.getId());
                            intent.putExtra("cityName", city.getName());
                            intent.putExtra("countryId", city.getCountryId());
                            startActivity(intent);
                        }));
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(RecommendedActivity.this,
                                "Nu am putut incarca orasele: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(RecommendedActivity.this,
                        "Nu am putut incarca profilul: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
}