package com.example.mobile_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class DiscoverActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── ACTIVARE TRANSPARENȚĂ STATUS BAR ──
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_discover);

        // ── GESTIONARE INSETS (EDGE-TO-EDGE) ──
        View rootLayout = findViewById(R.id.discoverRoot);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                // Păstrăm padding-ul tău inițial (doar pentru bottom, sau adaugă top/left/right după preferințe)
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        // Navigare planificare călătorie
        findViewById(R.id.btnPlanArrow).setOnClickListener(v -> {
            startActivity(new Intent(DiscoverActivity.this, PlanTripActivity.class));
        });

        findViewById(R.id.btnAI).setOnClickListener(v -> {
            startActivity(new Intent(DiscoverActivity.this, CityDetailActivity.class));
        });

        findViewById(R.id.searchBar).setOnClickListener(v -> {
            startActivity(new Intent(DiscoverActivity.this, SearchActivity.class));
        });

        // Navigare setări cont apăsând pe iconița/avatarul de sus
        TextView txtAvatar = findViewById(R.id.txtAvatar);
        if (txtAvatar != null) {
            txtAvatar.setOnClickListener(v -> {
                Intent intent = new Intent(DiscoverActivity.this, AccountSettingsActivity.class);
                startActivity(intent);
            });
        }

        // ── INIȚIALIZARE BOTTOM NAVIGATION MENTALITATE TAB-URI ──
        LinearLayout bottomNav = findViewById(R.id.bottomNav);

        if (bottomNav != null) {
            View btnNavDiscover = bottomNav.getChildAt(0); // Primul element: Discover (Curent)
            View btnNavMap      = bottomNav.getChildAt(1); // Al doilea element: Map (Explore)
            View btnNavWishlist = bottomNav.getChildAt(2); // Al treilea element: Wishlist (Journey)
            View btnNavAi       = bottomNav.getChildAt(3); // Al patrulea element: AI

            // 1. Discover devine activ (Turcoaz `@color/light_turq_1`)
            if (btnNavDiscover instanceof LinearLayout) {
                LinearLayout discoverLayout = (LinearLayout) btnNavDiscover;
                ImageView imgDiscover = (ImageView) discoverLayout.getChildAt(0);
                TextView txtDiscover = (TextView) discoverLayout.getChildAt(1);

                int turqColor = ContextCompat.getColor(this, R.color.light_turq_1);
                if (imgDiscover != null) imgDiscover.setColorFilter(turqColor);
                if (txtDiscover != null) {
                    txtDiscover.setTextColor(turqColor);
                    txtDiscover.setTypeface(null, android.graphics.Typeface.BOLD);
                }
            }

            // 2. Map devine inactiv (Gri) în caz că preia stări reziduale din XML
            if (btnNavMap instanceof LinearLayout) {
                LinearLayout mapLayout = (LinearLayout) btnNavMap;
                ImageView imgMap = (ImageView) mapLayout.getChildAt(0);
                TextView txtMap = (TextView) mapLayout.getChildAt(1);
                if (imgMap != null) imgMap.setColorFilter(ContextCompat.getColor(this, android.R.color.darker_gray));
                if (txtMap != null) {
                    txtMap.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                    txtMap.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            }

            // ── ASCULTĂTORI CLICK PENTRU SCHIMBAREA ECRANELOR ──
            if (btnNavMap != null) {
                btnNavMap.setOnClickListener(v -> {
                    Intent intent = new Intent(DiscoverActivity.this, ExploreActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            if (btnNavWishlist != null) {
                btnNavWishlist.setOnClickListener(v -> {
                    Intent intent = new Intent(DiscoverActivity.this, JourneyActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            if (btnNavAi != null) {
                btnNavAi.setOnClickListener(v -> {
                    overridePendingTransition(0, 0);
                });
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}