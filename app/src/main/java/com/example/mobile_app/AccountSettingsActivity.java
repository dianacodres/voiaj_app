package com.example.mobile_app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AccountSettingsActivity extends AppCompatActivity {

    private static final int MIN_REQUIRED = 2;
    private static final String RESOURCE_PREFIX = "interests_";

    private TextView txtUserName, txtUserEmail;
    private final List<InterestCard> popCards = new ArrayList<>();
    private final Set<String> selectedKeys = new HashSet<>();

    private AuthRepository authRepository;
    private User currentUser; // profilul curent, incarcat din Firestore

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
        setContentView(R.layout.activity_profile_settings);

        View headerBackground = findViewById(R.id.headerBackground);
        if (headerBackground != null) {
            int skyBlueColor = ContextCompat.getColor(this, R.color.sky_blue_2);
            headerBackground.setBackgroundColor(skyBlueColor);
        }

        int designBackgroundColor = Color.parseColor("#BDD8E9");
        getWindow().setBackgroundDrawable(new ColorDrawable(designBackgroundColor));

        View mainRoot = findViewById(R.id.mainRoot);
        if (mainRoot == null) {
            mainRoot = ((android.view.ViewGroup) findViewById(android.view.ViewGroup.NO_ID)).getChildAt(0);
        }

        if (mainRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(
                        WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars()
                );
                v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        txtUserName = findViewById(R.id.txtUserName);
        txtUserEmail = findViewById(R.id.txtUserEmail);

        authRepository = new AuthRepository();
        loadCurrentUserProfile();

        // 1. POP-UP PROFIL (doar numele se salveaza in Firestore; emailul e doar afisat)
        findViewById(R.id.btnEditProfile).setOnClickListener(v -> {
            BottomSheetDialog profileDialog = new BottomSheetDialog(this);
            View view = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_edit_profile, null);
            profileDialog.setContentView(view);
            profileDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

            EditText edtPopName = view.findViewById(R.id.edtPopName);
            EditText edtPopEmail = view.findViewById(R.id.edtPopEmail);
            edtPopName.setText(txtUserName.getText().toString());
            edtPopEmail.setText(txtUserEmail.getText().toString());
            edtPopEmail.setEnabled(false); // schimbarea emailului necesita flux separat de re-autentificare

            view.findViewById(R.id.btnDiscardProfile).setOnClickListener(db -> profileDialog.dismiss());
            view.findViewById(R.id.btnSaveProfile).setOnClickListener(sb -> {
                String newName = edtPopName.getText().toString().trim();
                if (TextUtils.isEmpty(newName)) {
                    Toast.makeText(this, "Numele nu poate fi gol", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (currentUser == null) {
                    Toast.makeText(this, "Profilul nu a fost incarcat inca.", Toast.LENGTH_SHORT).show();
                    return;
                }

                authRepository.updateName(currentUser.getUid(), newName, new AuthRepository.AuthCallback() {
                    @Override
                    public void onSuccess(User user) {
                        currentUser = user;
                        txtUserName.setText(user.getName());
                        profileDialog.dismiss();
                        Toast.makeText(AccountSettingsActivity.this, "Profil actualizat", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(AccountSettingsActivity.this,
                                "Eroare la salvare: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            });

            profileDialog.show();
        });

        // 2. POP-UP PAROLA (doar pentru conturi cu authProvider = email)
        findViewById(R.id.btnMenuChangePassword).setOnClickListener(v -> {
            if (currentUser != null && "google".equals(currentUser.getAuthProvider())) {
                Toast.makeText(this,
                        "Contul tau e conectat prin Google, parola se gestioneaza din contul Google.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            BottomSheetDialog passwordDialog = new BottomSheetDialog(this);
            View passView = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_password, null);
            passwordDialog.setContentView(passView);
            passwordDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

            EditText edtCurrentPassword = passView.findViewById(R.id.edtCurrentPassword);
            EditText edtNewPassword = passView.findViewById(R.id.edtNewPassword);
            MaterialButton btnSubmit = passView.findViewById(R.id.btnUpdatePasswordSubmit);

            btnSubmit.setOnClickListener(view -> {
                String currentPassword = edtCurrentPassword.getText().toString();
                String newPassword = edtNewPassword.getText().toString();

                if (TextUtils.isEmpty(currentPassword)) {
                    edtCurrentPassword.setError("Introdu parola curenta");
                    return;
                }
                if (TextUtils.isEmpty(newPassword) || newPassword.length() < 6) {
                    edtNewPassword.setError("Parola noua trebuie sa aiba minim 6 caractere");
                    return;
                }

                btnSubmit.setEnabled(false);
                authRepository.changePassword(currentPassword, newPassword, new AuthRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(AccountSettingsActivity.this, "Parola a fost schimbata", Toast.LENGTH_SHORT).show();
                        passwordDialog.dismiss();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        btnSubmit.setEnabled(true);
                        Toast.makeText(AccountSettingsActivity.this,
                                "Nu am putut schimba parola: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            });

            passView.findViewById(R.id.txtPopUpForgotPassword).setOnClickListener(view -> passwordDialog.dismiss());
            passwordDialog.show();
        });

        // 3. NOTIFICATII (local, UI only - fara camp in Firestore momentan)
        SwitchMaterial switchNotifications = findViewById(R.id.switchNotifications);

        int colorActiveThumb = ContextCompat.getColor(this, R.color.sky_blue_2);
        int colorMutedThumb = ContextCompat.getColor(this, R.color.text_muted);

        switchNotifications.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                switchNotifications.setThumbTintList(ColorStateList.valueOf(colorActiveThumb));
                switchNotifications.setTrackTintList(ColorStateList.valueOf(colorActiveThumb));
            } else {
                switchNotifications.setThumbTintList(ColorStateList.valueOf(colorMutedThumb));
                switchNotifications.setTrackTintList(ColorStateList.valueOf(colorMutedThumb));
            }
        });

        if (!switchNotifications.isChecked()) {
            switchNotifications.setThumbTintList(ColorStateList.valueOf(colorMutedThumb));
            switchNotifications.setTrackTintList(ColorStateList.valueOf(colorMutedThumb));
        }

        // 4. POP-UP INTERESE (acum salveaza in Firestore, nu doar SharedPreferences)
        findViewById(R.id.btnMenuChangeInterests).setOnClickListener(v -> {
            BottomSheetDialog interestsDialog = new BottomSheetDialog(this);
            View intView = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_interests, null);
            interestsDialog.setContentView(intView);
            interestsDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

            popCards.clear();
            selectedKeys.clear();
            if (currentUser != null && currentUser.getInterests() != null) {
                selectedKeys.addAll(currentUser.getInterests());
            }

            // Keys aliniate cu schema reala din Firestore (beaches, food, cities, mountains, culture, nature)
            CardConfig[] configs = {
                    new CardConfig(R.id.cardBeaches, "beaches", "Beaches", "prelogin_img_1"),
                    new CardConfig(R.id.cardFood, "food", "Food", "prelogin_img_2"),
                    new CardConfig(R.id.cardCities, "cities", "Cities", "prelogin_img_3"),
                    new CardConfig(R.id.cardMountains, "mountains", "Mountains", "prelogin_img_4"),
                    new CardConfig(R.id.cardCulture, "culture", "Culture", "prelogin_img_5"),
                    new CardConfig(R.id.cardNature, "nature", "Nature", "prelogin_img_6")
            };

            MaterialButton btnSaveInterests = intView.findViewById(R.id.btnSaveInterests);

            for (CardConfig config : configs) {
                setupPopCard(intView, config.id, config.key, config.label, config.img, btnSaveInterests);
            }

            boolean isReady = selectedKeys.size() >= MIN_REQUIRED;
            btnSaveInterests.setEnabled(isReady);
            btnSaveInterests.setAlpha(isReady ? 1.0f : 0.5f);

            btnSaveInterests.setOnClickListener(sv -> {
                if (selectedKeys.size() < MIN_REQUIRED || currentUser == null) return;

                btnSaveInterests.setEnabled(false);
                authRepository.updateInterests(currentUser.getUid(), new ArrayList<>(selectedKeys),
                        new AuthRepository.AuthCallback() {
                            @Override
                            public void onSuccess(User user) {
                                currentUser = user;
                                Toast.makeText(AccountSettingsActivity.this, "Interese actualizate", Toast.LENGTH_SHORT).show();
                                interestsDialog.dismiss();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                btnSaveInterests.setEnabled(true);
                                Toast.makeText(AccountSettingsActivity.this,
                                        "Eroare: " + errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
            });

            interestsDialog.show();
        });

        // 5. LOG OUT
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            authRepository.signOut();
            Intent intent = new Intent(AccountSettingsActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    /** Incarca profilul userului logat curent din Firestore si populeaza ecranul. */
    private void loadCurrentUserProfile() {
        authRepository.getCurrentUserProfile(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
                txtUserName.setText(user.getName());
                txtUserEmail.setText(user.getEmail());
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(AccountSettingsActivity.this,
                        "Nu am putut incarca profilul: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupPopCard(View parent, int includeRootId, String key, String label, String drawableName, MaterialButton submitBtn) {
        View root = parent.findViewById(includeRootId);
        ImageView ivImage = root.findViewById(R.id.ivImage);
        TextView tvLabel = root.findViewById(R.id.tvLabel);
        FrameLayout badgeBg = root.findViewById(R.id.badgeBg);
        ImageView badgeIcon = root.findViewById(R.id.badgeIcon);

        tvLabel.setText(label);
        try {
            String fullDrawableName = RESOURCE_PREFIX + drawableName;
            int imageResId = getResources().getIdentifier(fullDrawableName, "drawable", getPackageName());
            if (imageResId != 0) ivImage.setImageResource(imageResId);
        } catch (Exception ignored) {}

        boolean isAlreadySelected = selectedKeys.contains(key);
        root.setSelected(isAlreadySelected);
        badgeBg.setSelected(isAlreadySelected);
        badgeIcon.setImageResource(R.drawable.interests_ic_plus);
        if (isAlreadySelected) badgeIcon.setRotation(45f);

        InterestCard card = new InterestCard(key, root, badgeBg, badgeIcon);
        popCards.add(card);

        root.setOnClickListener(v -> {
            boolean nowSelected = !card.root.isSelected();
            card.root.setSelected(nowSelected);
            card.badgeBg.setSelected(nowSelected);

            card.badgeIcon.animate().rotation(nowSelected ? 45f : 0f).setDuration(100).start();

            if (nowSelected) selectedKeys.add(card.key);
            else selectedKeys.remove(card.key);

            boolean isReady = selectedKeys.size() >= MIN_REQUIRED;
            submitBtn.setEnabled(isReady);
            submitBtn.setAlpha(isReady ? 1.0f : 0.5f);
        });
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