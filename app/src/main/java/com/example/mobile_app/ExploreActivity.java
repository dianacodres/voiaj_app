package com.example.mobile_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ExploreActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explore);

        // ── SOLUȚIE: Preluăm bottomNav direct ca LinearLayout pentru a putea folosi getChildAt() ──
        LinearLayout bottomNav = findViewById(R.id.bottomNav);

        if (bottomNav != null) {
            View btnNavDiscover = bottomNav.getChildAt(0); // Primul element: Discover
            View btnNavWishlist = bottomNav.getChildAt(2); // Al treilea element: Wishlist
            View btnNavAi       = bottomNav.getChildAt(3); // Al patrulea element: AI

            // ── LOGICA DE NAVIGARE ──
            if (btnNavDiscover != null) {
                btnNavDiscover.setOnClickListener(v -> {
                    Intent intent = new Intent(ExploreActivity.this, DiscoverActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            if (btnNavWishlist != null) {
                btnNavWishlist.setOnClickListener(v -> {
                    Intent intent = new Intent(ExploreActivity.this, JourneyActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            if (btnNavAi != null) {
                btnNavAi.setOnClickListener(v -> {
                    // Adaugă aici intent-ul pentru AI când ai activitatea pregătită
                    Toast.makeText(ExploreActivity.this, "AI Section", Toast.LENGTH_SHORT).show();
                });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}