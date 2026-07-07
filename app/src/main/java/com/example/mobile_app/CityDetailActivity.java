package com.example.mobile_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class CityDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_city_detail);

        // Bara de status transparenta
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.cityDetailRoot), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(0, 0, 0, systemBars.bottom);
                    return insets;
                });

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.cityDetailRoot), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    // Aplica padding de top pe AppBarLayout, nu pe root
                    findViewById(R.id.appBarLayout).setPadding(0, systemBars.top, 0, 0);
                    v.setPadding(0, 0, 0, systemBars.bottom);
                    return insets;
                });
        // Buton inapoi
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Buton Plan Trip
        findViewById(R.id.btnPlanTrip).setOnClickListener(v -> {
            startActivity(new Intent(CityDetailActivity.this, PlanTripActivity.class));
        });

        // Buton Wishlist - placeholder
        findViewById(R.id.btnAddWishlist).setOnClickListener(v -> {
            // TODO: adauga la wishlist Firebase
        });
    }
}