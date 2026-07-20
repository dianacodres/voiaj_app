package com.example.mobile_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
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
public class RecommendedActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_recommended);

        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // Forțăm pictogramele de sistem să fie albe (sau dark, în funcție de fundalul tău)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                // Setează 0 pentru iconițe albe, sau APPEARANCE_LIGHT_STATUS_BARS pentru negre
                controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }

        // 2. Padding pentru a lăsa conținutul să urce sub bara de status, fără "bandă albă"
        View root = findViewById(R.id.recommendedRoot);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                // Padding jos pentru navigație, sus lăsăm 0 ca să treacă sub status bar
                v.setPadding(0, 0, 0, systemBars.bottom);
                return insets;
            });
        }

        // 3. Padding pentru Header-ul tău (dacă ai unul cu ID-ul "header")
        // ca să împingem textul/butoanele sub ceas
        View header = findViewById(R.id.header);
        if (header != null) {
            ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingAnimation = findViewById(R.id.loadingAnimation);
        showLoading();
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
                        hideLoading();
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
                        hideLoading();
                        Toast.makeText(RecommendedActivity.this,
                                "Couldn't load cities: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(RecommendedActivity.this,
                        "Couldn't load profile: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }
}