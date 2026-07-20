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
public class RecommendedActivity extends AppCompatActivity { // Schimbat din BaseActivity pentru a evita conflictele cu Lottie

    private View layoutLoadingScreen; // Noua variabilă pentru masca nativă turcoaz

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_recommended);

        // 1. Inițializăm elementul de loading din noul XML nativ
        layoutLoadingScreen = findViewById(R.id.layoutLoadingScreen);

        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }

        // 2. Gestionare corectă a Insets într-un singur loc pentru a preveni bug-ul de suprascriere
        View root = findViewById(R.id.recommendedRoot);
        View header = findViewById(R.id.header);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                // Aplicăm padding-ul de jos pe root pentru bara de navigație
                v.setPadding(0, 0, 0, systemBars.bottom);

                // Aplicăm padding-ul de sus pe header pentru a împinge conținutul sub ceas/Status Bar
                if (header != null) {
                    header.setPadding(header.getPaddingLeft(), systemBars.top, header.getPaddingRight(), header.getPaddingBottom());
                }

                return insets;
            });
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadRecommendations();
    }

    private void loadRecommendations() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            ascundeLoadingFluid();
            return;
        }

        AuthRepository authRepository = new AuthRepository();
        authRepository.getCurrentUserProfile(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(User user) {
                CityRepository cityRepository = new CityRepository();
                cityRepository.getAllCities(new CityRepository.CityListCallback() {
                    @Override
                    public void onSuccess(List<City> cities) {
                        // ── ASCUNDEM MASCA FLUID ACUM CÂND DATELE AU SOSIT ──
                        ascundeLoadingFluid();

                        List<RecommendationEngine.CityMatch> ranked =
                                RecommendationEngine.rankCities(user.getInterests(), cities);

                        RecyclerView recyclerView = findViewById(R.id.recyclerRecommended);
                        if (recyclerView != null) {
                            recyclerView.setLayoutManager(new LinearLayoutManager(RecommendedActivity.this));
                            recyclerView.setAdapter(new RecommendedCityAdapter(ranked, city -> {
                                Intent intent = new Intent(RecommendedActivity.this, CityDetailActivity.class);
                                intent.putExtra("cityId", city.getId());
                                intent.putExtra("cityName", city.getName());
                                intent.putExtra("countryId", city.getCountryId());
                                startActivity(intent);
                            }));
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        ascundeLoadingFluid(); // Ascundem masca și în caz de eroare ca să nu se blocheze interfața
                        Toast.makeText(RecommendedActivity.this,
                                "Couldn't load cities: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                ascundeLoadingFluid(); // Ascundem masca și în caz de eroare la profil
                Toast.makeText(RecommendedActivity.this,
                        "Couldn't load profile: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Metodă helper pentru ascunderea fluidă cu fade-out a ecranului nativ de încărcare */
    private void ascundeLoadingFluid() {
        if (layoutLoadingScreen != null && layoutLoadingScreen.getVisibility() == View.VISIBLE) {
            layoutLoadingScreen.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> layoutLoadingScreen.setVisibility(View.GONE))
                    .start();
        }
    }
}