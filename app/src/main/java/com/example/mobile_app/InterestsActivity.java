package com.example.mobile_app;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

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
    private MaterialButton btnContinue;
    private FrameLayout loadingOverlay; // Adăugat pentru ecranul de loading unificat

    private final List<InterestCard> cards = new ArrayList<>();
    private final Set<String> selectedKeys = new HashSet<>();

    private static final String RESOURCE_PREFIX = "interests_";

    private AuthRepository authRepository;
    private String pendingMode;
    private String pendingUid;
    private String pendingName;
    private String pendingEmail;
    private String pendingPassword;

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

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_interests);

        if (findViewById(R.id.main) != null) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        authRepository = new AuthRepository();
        pendingMode = getIntent().getStringExtra("mode");
        pendingUid = getIntent().getStringExtra("uid");
        pendingName = getIntent().getStringExtra("name");
        pendingEmail = getIntent().getStringExtra("email");
        pendingPassword = getIntent().getStringExtra("password");

        progressBar = findViewById(R.id.progressBar);
        tvStepCount = findViewById(R.id.tvStepCount);

        // Identificare overlay loading din XML (asigură-te că ai un FrameLayout cu acest ID ascuns în XML)
        loadingOverlay = findViewById(R.id.loadingOverlay);

        try {
            tvStepCount.setText(R.string.step_two_of_two);
        } catch (Exception e) {
            tvStepCount.setText("2/2");
        }

        btnContinue = findViewById(R.id.btnContinue);

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            ObjectAnimator animator = ObjectAnimator.ofInt(progressBar, "progress", progressBar.getProgress(), 50);
            animator.setDuration(200);
            animator.start();
            progressBar.postDelayed(this::finish, 200);
        });

        CardConfig[] configs = {
                new CardConfig(R.id.cardBeaches, "beaches", "Beaches", "prelogin_img_1"),
                new CardConfig(R.id.cardFood, "food", "Food", "prelogin_img_2"),
                new CardConfig(R.id.cardCities, "historical", "Historical", "prelogin_img_3"),
                new CardConfig(R.id.cardMountains, "urbanism", "Urbanism", "prelogin_img_4"),
                new CardConfig(R.id.cardCulture, "culture", "Culture", "prelogin_img_5"),
                new CardConfig(R.id.cardNature, "nature", "Nature", "prelogin_img_6")
        };

        for (CardConfig config : configs) {
            setupCard(config.id, config.key, config.label, config.img);
        }

        updateContinueState();

        btnContinue.setOnClickListener(v -> {
            if (selectedKeys.size() < MIN_REQUIRED) {
                try {
                    Toast.makeText(this, R.string.min_select_error, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Te rugăm să selectezi minimum 2 interese.", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            saveInterestsToPreferences();
            animateProgressToStepTwo();
        });
    }

    private void setupCard(int includeRootId, String key, String label, String drawableName) {
        View root = findViewById(includeRootId);
        if (root == null) return;

        ImageView ivImage = root.findViewById(R.id.ivImage);
        TextView tvLabel = root.findViewById(R.id.tvLabel);
        FrameLayout badgeBg = root.findViewById(R.id.badgeBg);
        ImageView badgeIcon = root.findViewById(R.id.badgeIcon);

        if (tvLabel != null) tvLabel.setText(label);
        if (ivImage != null) loadImageSafely(ivImage, drawableName, label);

        if (badgeIcon != null) badgeIcon.setImageResource(R.drawable.interests_ic_plus);

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
                    imageView.setImageResource(android.R.color.darker_gray);
                }
            }
        } catch (Exception e) {
            imageView.setImageResource(android.R.color.darker_gray);
        }
    }

    private void toggleCard(InterestCard card) {
        if (card == null || card.root == null) return;
        boolean nowSelected = !card.root.isSelected();

        card.root.setSelected(nowSelected);
        if (card.badgeBg != null) card.badgeBg.setSelected(nowSelected);

        if (card.badgeIcon != null) {
            float targetRotation = nowSelected ? 45f : 0f;
            card.badgeIcon.animate()
                    .rotation(targetRotation)
                    .setDuration(100)
                    .start();
        }

        if (nowSelected) {
            selectedKeys.add(card.key);
        } else {
            selectedKeys.remove(card.key);
        }

        updateContinueState();
    }

    private void updateContinueState() {
        boolean isReady = selectedKeys.size() >= MIN_REQUIRED;
        btnContinue.setEnabled(isReady);

        if (isReady) {
            btnContinue.setAlpha(1.0f);
        } else {
            btnContinue.setAlpha(0.5f);
        }
    }

    private void saveInterestsToPreferences() {
        SharedPreferences sharedPref = getSharedPreferences("VoiajAppPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putStringSet("user_interests", selectedKeys);
        editor.apply();
    }

    private void animateProgressToStepTwo() {
        btnContinue.setEnabled(false);
        ObjectAnimator animator = ObjectAnimator.ofInt(progressBar, "progress", progressBar.getProgress(), 100);
        animator.setDuration(350);
        animator.start();

        try {
            tvStepCount.setText(R.string.step_two_of_two);
        } catch (Exception e) {
            tvStepCount.setText("2/2");
        }

        // Afișăm loadingscreen-ul chiar de aici din onboarding pentru continuitate
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }

        progressBar.postDelayed(this::createFirebaseAccount, 400);
    }

    private void createFirebaseAccount() {
        List<String> interests = new ArrayList<>(selectedKeys);

        if ("google_new".equals(pendingMode)) {
            createGoogleAccount(interests);
        } else {
            createEmailAccount(interests);
        }
    }

    private void createEmailAccount(List<String> interests) {
        if (pendingName == null || pendingEmail == null || pendingPassword == null) {
            Toast.makeText(this, "Missing data from the previous step. Please restart signup.", Toast.LENGTH_LONG).show();
            hideLoadingOverlay();
            return;
        }

        authRepository.signUpWithEmail(pendingName, pendingEmail, pendingPassword, interests,
                new AuthRepository.AuthCallback() {
                    @Override
                    public void onSuccess(User user) {
                        goToDiscover();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        showCreationError("Couldn't create the account: " + errorMessage);
                    }
                });
    }

    private void createGoogleAccount(List<String> interests) {
        if (pendingUid == null || pendingEmail == null) {
            Toast.makeText(this, "Missing data from the previous step. Please restart sign-in.", Toast.LENGTH_LONG).show();
            hideLoadingOverlay();
            return;
        }

        User partialUser = new User(pendingName, pendingEmail, "google", new ArrayList<>());
        partialUser.setUid(pendingUid);

        authRepository.saveNewGoogleUser(partialUser, interests, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(User user) {
                goToDiscover();
            }

            @Override
            public void onError(String errorMessage) {
                showCreationError("Couldn't save the profile: " + errorMessage);
            }
        });
    }
    private void goToDiscover() {
        Intent intent = new Intent(InterestsActivity.this, DiscoverActivity.class);
        intent.putExtra("selected_count", selectedKeys.size());
        intent.putExtra("IS_UNIFIED_SIGNUP_LOADING", true);

        // ── BLOCARE TOTALĂ ANIMAȚIE NATIVĂ DE TRANZIȚIE ──
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void showCreationError(String message) {
        Toast.makeText(InterestsActivity.this, message, Toast.LENGTH_LONG).show();
        hideLoadingOverlay();
        progressBar.setProgress(50);
        tvStepCount.setText("2/2");
    }

    private void hideLoadingOverlay() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
        btnContinue.setEnabled(true);
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

    @Override
    protected void onStop() {
        super.onStop();

        if (loadingOverlay != null && loadingOverlay.getVisibility() == View.VISIBLE) {
            finish();
        }
    }
}