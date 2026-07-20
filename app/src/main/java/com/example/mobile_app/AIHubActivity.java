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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.Map;

/**
 * Ecranul principal de AI ("AI Hub") - acces la conversatii noi, quick start-uri
 * si conversatii recente (DOAR itinerarii, salvate cu adevarat - nu vreme/buget).
 */
public class AIHubActivity extends AppCompatActivity {

    private LinearLayout itemRecent1, itemRecent2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_aihub);

        Window window = getWindow();
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // Forțăm iconițele de sistem să fie albe (sau închise, în funcție de fundalul tău)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                // Setează 0 pentru iconițe albe
                controller.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }

        // 2. PADDING UNITAR (Lăsăm conținutul să urce sub status bar)
        View root = findViewById(R.id.aiHubRoot);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                // Padding jos pentru navigare, restul conținutului urcă sub ceas
                v.setPadding(0, 0, 0, systemBars.bottom);

                // Dacă ai un header cu ID-ul "header" în XML, aplică padding-ul acolo ca să nu fie textul sub ceas
                // View header = findViewById(R.id.header);
                // if (header != null) header.setPadding(0, systemBars.top, 0, 0);

                return insets;
            });
        }

        findViewById(R.id.btnNewConversation).setOnClickListener(v -> openChatAskAnything());

        findViewById(R.id.cardPlanItinerary).setOnClickListener(v -> openChat("Plan an itinerary for me"));
        findViewById(R.id.cardWeatherSeason).setOnClickListener(v -> openChat("What's the best time to visit?"));
        findViewById(R.id.cardBudgetEstimate).setOnClickListener(v -> openChat("Estimate my trip budget"));
        findViewById(R.id.cardAskAnything).setOnClickListener(v -> openChat("Where should I go on vacation?"));

        itemRecent1 = findViewById(R.id.itemRecent1);
        itemRecent2 = findViewById(R.id.itemRecent2);

        incarcaIstoricRecent();
        setupBottomNav();
    }

    /** Incarca ultimele 2 itinerarii SALVATE ale userului, ca "Recent" - nu conversatii de vreme/buget. */
    private void incarcaIstoricRecent() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        new TripRepository().getRecentChatHistory(uid, 2, new TripRepository.ChatHistoryCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> intrari) {
                if (intrari.isEmpty()) {
                    if (itemRecent1 != null) itemRecent1.setVisibility(View.GONE);
                    if (itemRecent2 != null) itemRecent2.setVisibility(View.GONE);
                    return;
                }

                if (intrari.size() > 0) populeazaRandRecent(itemRecent1, intrari.get(0));
                else if (itemRecent1 != null) itemRecent1.setVisibility(View.GONE);

                if (intrari.size() > 1) populeazaRandRecent(itemRecent2, intrari.get(1));
                else if (itemRecent2 != null) itemRecent2.setVisibility(View.GONE);
            }

            @Override
            public void onError(String errorMessage) {
                android.util.Log.e("AIHub", "Nu s-a putut incarca istoricul recent: " + errorMessage);
            }
        });
    }

    private void populeazaRandRecent(LinearLayout rand, Map<String, Object> intrare) {
        if (rand == null) return;

        String cityName = (String) intrare.get("cityName");
        Object daysCountObj = intrare.get("daysCount");
        long daysCount = daysCountObj instanceof Long ? (Long) daysCountObj : 0;
        String tripId = (String) intrare.get("tripId");

        TextView txtLabel = (TextView) rand.getChildAt(1);
        if (txtLabel != null) {
            txtLabel.setText(cityName + " · " + daysCount + "-day itinerary");
        }

        Object createdAtObj = intrare.get("createdAt");
        TextView txtTimp = (TextView) rand.getChildAt(2);
        if (txtTimp != null && createdAtObj instanceof com.google.firebase.Timestamp) {
            long momentSalvare = ((com.google.firebase.Timestamp) createdAtObj).toDate().getTime();
            txtTimp.setText(calculeazaTimpRelativ(momentSalvare));
        }

        rand.setOnClickListener(v -> {
            Intent intent = new Intent(this, AIChatActivity.class);
            intent.putExtra("viewSavedTripId", tripId);
            startActivity(intent);
        });
    }

    /** Timp relativ simplu ("5m", "3h", "2d") fata de acum. */
    private String calculeazaTimpRelativ(long momentTrecut) {
        long diferentaMs = System.currentTimeMillis() - momentTrecut;
        long minute = diferentaMs / (1000 * 60);
        long ore = minute / 60;
        long zile = ore / 24;

        if (zile > 0) return zile + "d";
        if (ore > 0) return ore + "h";
        if (minute > 0) return minute + "m";
        return "now";
    }

    private void openChat(String initialPrompt) {
        Intent intent = new Intent(this, AIChatActivity.class);
        if (initialPrompt != null) intent.putExtra("initialPrompt", initialPrompt);
        startActivity(intent);
    }

    private void openChatAskAnything() {
        Intent intent = new Intent(this, AIChatActivity.class);
        intent.putExtra("askAnythingMode", true);
        startActivity(intent);
    }

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