package com.example.mobile_app;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Step 2/2: "What inspires your trips?"
 */
public class InterestsActivity extends AppCompatActivity {


    private static final int MIN_REQUIRED = 2;

    private ProgressBar progressBar;
    private TextView tvStepCount;
    private Button btnContinue;

    private final List<InterestCard> cards = new ArrayList<>();
    private final Set<String> selectedKeys = new HashSet<>();

    private static final String RESOURCE_PREFIX = "interests_";

    private static class CardConfig {
        final int id;
        final String key;
        final String label;
        final String img;

        CardConfig(int id, String key, String label, String img) {
            this.id = id;
            this.key = key;
            this.label = label;
            this.img = img;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_interests);

        progressBar = findViewById(R.id.progressBar);
        tvStepCount = findViewById(R.id.tvStepCount);
        btnContinue = findViewById(R.id.btnContinue);

        findViewById(R.id.btnBack).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        CardConfig[] configs = {
                new CardConfig(R.id.cardBeaches, "beaches", "Beaches", "prelogin_img_1"),
                new CardConfig(R.id.cardFood, "food", "Food", "prelogin_img_2"),
                new CardConfig(R.id.cardCities, "cities", "Cities", "prelogin_img_3"),
                new CardConfig(R.id.cardMountains, "mountains", "Mountains", "prelogin_img_4"),
                new CardConfig(R.id.cardCulture, "culture", "Culture", "prelogin_img_5"),
                new CardConfig(R.id.cardNature, "nature", "Nature", "prelogin_img_6")
        };

        for (CardConfig config : configs) {
            setupCard(config.id, config.key, config.label, config.img);
        }

        updateContinueState();

        btnContinue.setOnClickListener(v -> {
            if (selectedKeys.size() < MIN_REQUIRED) {
                Toast.makeText(this, R.string.min_select_error, Toast.LENGTH_SHORT).show();
                return;
            }


            saveInterestsToPreferences();

            animateProgressToStepTwo();
        });
    }

    private void setupCard(int includeRootId, String key, String label, String drawableName) {
        View root = findViewById(includeRootId);

        ImageView ivImage = root.findViewById(R.id.ivImage);
        TextView tvLabel = root.findViewById(R.id.tvLabel);
        FrameLayout badgeBg = root.findViewById(R.id.badgeBg);
        ImageView badgeIcon = root.findViewById(R.id.badgeIcon);

        tvLabel.setText(label);
        loadImageSafely(ivImage, drawableName, label);

        // Setăm iconița inițială ca fiind obligatoriu plusul (neselectat)
        badgeIcon.setImageResource(R.drawable.interests_ic_plus);

        InterestCard card = new InterestCard(key, root, badgeBg, badgeIcon);
        cards.add(card);

        root.setOnClickListener(v -> toggleCard(card));
    }

    private void loadImageSafely(ImageView imageView, String drawableName, String label) {
        try {
            String fullDrawableName = RESOURCE_PREFIX + drawableName;
            int imageResId = getResources().getIdentifier(fullDrawableName, "drawable", getPackageName());

            if (imageResId != 0) {
                imageView.setImageResource(imageResId);
            } else {
                int colorResId = getResources().getIdentifier(RESOURCE_PREFIX + "clr_prelogin_fallback", "drawable", getPackageName());

                if (colorResId != 0) {
                    imageView.setImageResource(colorResId);
                } else {
                    throw new Exception("Nu s-a găsit nicio resursă grafică sau de fallback universal.");
                }
            }
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    getString(R.string.img_missing_error, label, drawableName),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void toggleCard(InterestCard card) {
        if (card == null) return;
        boolean nowSelected = !card.root.isSelected();

        card.root.setSelected(nowSelected);
        card.badgeBg.setSelected(nowSelected);

        // Sugestia 2: Animație de rotație fluidă.
        // Dacă e neselectat, un plus (+). Dacă se rotește la 45 de grade, devine un (x).
        float targetRotation = nowSelected ? 45f : 0f;
        card.badgeIcon.animate()
                .rotation(targetRotation)
                .setDuration(250) // Durata animației în milisecunde
                .start();

        if (nowSelected) {
            selectedKeys.add(card.key);
        } else {
            selectedKeys.remove(card.key);
        }

        updateContinueState();
    }

    private void updateContinueState() {
        btnContinue.setEnabled(selectedKeys.size() >= MIN_REQUIRED);
    }

    // Sugestia 3: Implementarea metodei de salvare locală
    private void saveInterestsToPreferences() {
        SharedPreferences sharedPref = getSharedPreferences("VoiajAppPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        // Salvăm setul de string-uri în cache-ul telefonului
        editor.putStringSet("user_interests", selectedKeys);
        editor.apply();
    }

    private void animateProgressToStepTwo() {
        btnContinue.setEnabled(false);
        ObjectAnimator animator = ObjectAnimator.ofInt(progressBar, "progress", progressBar.getProgress(), 100);
        animator.setDuration(350);
        animator.start();
        tvStepCount.setText(R.string.step_two_of_two);

        progressBar.postDelayed(() -> {
            Intent intent = new Intent(this, SummaryActivity.class);
            intent.putExtra("selected_count", selectedKeys.size());
            startActivity(intent);
        }, 400);
    }

    private static class InterestCard {
        final String key;
        final View root;
        final FrameLayout badgeBg;
        final ImageView badgeIcon;

        InterestCard(String key, View root, FrameLayout badgeBg, ImageView badgeIcon) {
            this.key = key;
            this.root = root;
            this.badgeBg = badgeBg;
            this.badgeIcon = badgeIcon;
        }
    }
}