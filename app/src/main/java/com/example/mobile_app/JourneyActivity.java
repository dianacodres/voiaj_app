package com.example.mobile_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;

public class JourneyActivity extends AppCompatActivity {

    private LinearLayout containerActiveSection;
    private LinearLayout containerCompletedSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── 1. ACTIVARE EDGE-TO-EDGE FĂRĂ STRATUL GRI (SCRIM) ──
        // Forțăm sistemul să nu adauge nicio nuanță automată de fundal (scrim) deasupra barei de status
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.dark(Color.TRANSPARENT) // Primește un singur parametru!
        );
        setContentView(R.layout.activity_journey);

        // ── 2. ASIGURARE TRANSPARENȚĂ ȘI ICONIȚE NEGRE ──
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);

        // Dezactivăm explicit orice protecție de contrast la nivel de window, dacă există
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(true); // Schimbat numele metodei în cel nativ corect
        }

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(true); // Iconițe și ceas de culoare neagră
        }

        // ── GESTIONARE INSETS (PADDING) ──
        View rootLayout = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // ── INITIALIZARE CONTINEARE TRASURI ──
        containerActiveSection = findViewById(R.id.containerActiveSection);
        containerCompletedSection = findViewById(R.id.containerCompletedSection);

        // ── LOGICĂ SWITCH PENTRU TOGGLE BUTTON ──
        // ── LOGICĂ SWITCH CU COLORARE DINAMICĂ (JAVA OVERRIDE) ──
        MaterialButtonToggleGroup toggleGroupJourney = findViewById(R.id.toggleGroupJourney);
        com.google.android.material.button.MaterialButton btnTabActive = findViewById(R.id.btnTabActive);
        com.google.android.material.button.MaterialButton btnTabCompleted = findViewById(R.id.btnTabCompleted);

// Culorile extrase din resursele tale
        int turqColor = ContextCompat.getColor(this, R.color.light_turq_1);
        int creamColor = ContextCompat.getColor(this, R.color.cream);
        int navyColor = ContextCompat.getColor(this, R.color.navy_blue_1);

// Setăm starea vizuală inițială (Active e selectat la pornire)
        btnTabActive.setBackgroundColor(turqColor);
        btnTabActive.setTextColor(creamColor);
        btnTabCompleted.setBackgroundColor(Color.TRANSPARENT);
        btnTabCompleted.setTextColor(navyColor);

        toggleGroupJourney.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                if (checkedId == R.id.btnTabActive) {
                    // Schimbă panourile
                    containerActiveSection.setVisibility(View.VISIBLE);
                    containerCompletedSection.setVisibility(View.GONE);

                    // Colorează Active -> Full Turquoise + Text Crem
                    btnTabActive.setBackgroundColor(turqColor);
                    btnTabActive.setTextColor(creamColor);

                    // Resetează Completed -> Doar contur (Transparent interior) + Text Navy
                    btnTabCompleted.setBackgroundColor(Color.TRANSPARENT);
                    btnTabCompleted.setTextColor(navyColor);

                } else if (checkedId == R.id.btnTabCompleted) {
                    // Schimbă panourile
                    containerActiveSection.setVisibility(View.GONE);
                    containerCompletedSection.setVisibility(View.VISIBLE);

                    // Colorează Completed -> Full Turquoise + Text Crem
                    btnTabCompleted.setBackgroundColor(turqColor);
                    btnTabCompleted.setTextColor(creamColor);

                    // Resetează Active -> Doar contur (Transparent interior) + Text Navy
                    btnTabActive.setBackgroundColor(Color.TRANSPARENT);
                    btnTabActive.setTextColor(navyColor);
                }
            }
        });

        // ── INITIALIZARE BOTTOM NAVIGATION ──
        LinearLayout bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            View btnNavDiscover = bottomNav.getChildAt(0);
            View btnNavMap      = bottomNav.getChildAt(1);
            View btnNavWishlist = bottomNav.getChildAt(2);
            View btnNavAi       = bottomNav.getChildAt(3);

            if (btnNavMap instanceof LinearLayout) {
                LinearLayout mapLayout = (LinearLayout) btnNavMap;
                ImageView imgMap = (ImageView) mapLayout.getChildAt(0);
                TextView txtMap = (TextView) mapLayout.getChildAt(1);
                if (imgMap != null) imgMap.setImageTintList(null);
                if (imgMap != null) imgMap.setColorFilter(ContextCompat.getColor(this, android.R.color.darker_gray));
                if (txtMap != null) {
                    txtMap.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                    txtMap.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            }

            if (btnNavWishlist instanceof LinearLayout) {
                LinearLayout wishlistLayout = (LinearLayout) btnNavWishlist;
                ImageView imgWish = (ImageView) wishlistLayout.getChildAt(0);
                TextView txtWish = (TextView) wishlistLayout.getChildAt(1);

                if (imgWish != null) imgWish.setColorFilter(turqColor);
                if (txtWish != null) {
                    txtWish.setTextColor(turqColor);
                    txtWish.setTypeface(null, android.graphics.Typeface.BOLD);
                }
            }

            if (btnNavDiscover != null) {
                btnNavDiscover.setOnClickListener(v -> {
                    Intent intent = new Intent(JourneyActivity.this, DiscoverActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }

            if (btnNavMap != null) {
                btnNavMap.setOnClickListener(v -> {
                    Intent intent = new Intent(JourneyActivity.this, ExploreActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                });
            }
        }
    }
}