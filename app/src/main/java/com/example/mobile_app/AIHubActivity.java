package com.example.mobile_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Ecranul principal de AI ("AI Hub") - acces la conversatii noi, quick start-uri
 * si conversatii recente. Deocamdata doar UI, fara generare reala cu AI in spate
 * (functionalitate separata, planuita pentru mai tarziu).
 */
public class AIHubActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_aihub);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.aiHubRoot), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        findViewById(R.id.btnNewConversation).setOnClickListener(v -> openChat(null));
        findViewById(R.id.cardPlanItinerary).setOnClickListener(v -> openChat("Plan an itinerary for me"));
        findViewById(R.id.cardWeatherSeason).setOnClickListener(v -> openChat("What's the best time to visit?"));
        findViewById(R.id.cardBudgetEstimate).setOnClickListener(v -> openChat("Estimate my trip budget"));
        findViewById(R.id.cardAskAnything).setOnClickListener(v -> openChat(null));
        findViewById(R.id.itemRecent1).setOnClickListener(v -> openChat("Plan 4 days in Rome in May"));
        findViewById(R.id.itemRecent2).setOnClickListener(v -> openChat("Best time for Greece?"));

        setupBottomNav();
    }

    private void openChat(String initialPrompt) {
        Intent intent = new Intent(this, AIChatActivity.class);
        if (initialPrompt != null) intent.putExtra("initialPrompt", initialPrompt);
        startActivity(intent);
    }

    /** Bara de jos, cu tab-ul AI evidentiat (la fel ca in Discover/Journey, dar cu AI activ). */
    private void setupBottomNav() {
        LinearLayout bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav == null) return;

        View btnNavDiscover = bottomNav.getChildAt(0);
        View btnNavMap = bottomNav.getChildAt(1);
        View btnNavWishlist = bottomNav.getChildAt(2);

        if (btnNavDiscover != null) {
            btnNavDiscover.setOnClickListener(v -> {
                Intent intent = new Intent(AIHubActivity.this, DiscoverActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        if (btnNavMap != null) {
            btnNavMap.setOnClickListener(v -> {
                Intent intent = new Intent(AIHubActivity.this, ExploreActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }

        if (btnNavWishlist != null) {
            btnNavWishlist.setOnClickListener(v -> {
                Intent intent = new Intent(AIHubActivity.this, JourneyActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
            });
        }
    }
}