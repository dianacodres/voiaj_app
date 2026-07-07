package com.example.mobile_app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
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

        // 1. ACTIVARE EDGE-TO-EDGE
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_profile_settings);

        // 2. FIX CULOARE PENTRU HEADER BACKGROUND
        View headerBackground = findViewById(R.id.headerBackground);
        if (headerBackground != null) {
            int skyBlueColor = ContextCompat.getColor(this, R.color.sky_blue_2);
            headerBackground.setBackgroundColor(skyBlueColor);
        }

        // 3. FIX CULOARE FUNDAL BARE SISTEM
        int designBackgroundColor = Color.parseColor("#BDD8E9");
        getWindow().setBackgroundDrawable(new ColorDrawable(designBackgroundColor));

        // 4. APLICARE CORECTĂ A INSETS-URILOR (ADĂUGAT INSETS.TOP PENTRU MATRICEA DE SUS)
        View mainRoot = findViewById(R.id.mainRoot);
        if (mainRoot == null) {
            mainRoot = ((android.view.ViewGroup) findViewById(android.view.ViewGroup.NO_ID)).getChildAt(0);
        }

        if (mainRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainRoot, (v, windowInsets) -> {
                androidx.core.graphics.Insets insets = windowInsets.getInsets(
                        WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars()
                );
                // FIX CRITIC: insets.top se aplică acum ca padding superior, coborând butonul Back sub notch/ceas
                v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        txtUserName = findViewById(R.id.txtUserName);
        txtUserEmail = findViewById(R.id.txtUserEmail);

        // 1. POP-UP PROFIL
        findViewById(R.id.btnEditProfile).setOnClickListener(v -> {
            BottomSheetDialog profileDialog = new BottomSheetDialog(this);
            View view = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_edit_profile, null);
            profileDialog.setContentView(view);

            profileDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

            EditText edtPopName = view.findViewById(R.id.edtPopName);
            EditText edtPopEmail = view.findViewById(R.id.edtPopEmail);
            edtPopName.setText(txtUserName.getText().toString());
            edtPopEmail.setText(txtUserEmail.getText().toString());

            view.findViewById(R.id.btnDiscardProfile).setOnClickListener(db -> profileDialog.dismiss());
            view.findViewById(R.id.btnSaveProfile).setOnClickListener(sb -> {
                txtUserName.setText(edtPopName.getText().toString());
                txtUserEmail.setText(edtPopEmail.getText().toString());
                profileDialog.dismiss();
            });

            profileDialog.show();
        });

        // 2. POP-UP PAROLĂ
        findViewById(R.id.btnMenuChangePassword).setOnClickListener(v -> {
            BottomSheetDialog passwordDialog = new BottomSheetDialog(this);
            View passView = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_password, null);
            passwordDialog.setContentView(passView);

            passwordDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

            passView.findViewById(R.id.btnUpdatePasswordSubmit).setOnClickListener(view -> passwordDialog.dismiss());
            passView.findViewById(R.id.txtPopUpForgotPassword).setOnClickListener(view -> passwordDialog.dismiss());
            passwordDialog.show();
        });

        // 3. NOTIFICAȚII CU SCHIMBARE DE CULOARE DINAMICĂ
        SwitchMaterial switchNotifications = findViewById(R.id.switchNotifications);

        int colorActiveThumb = ContextCompat.getColor(this, R.color.sky_blue_2);
        int colorMutedThumb = ContextCompat.getColor(this, R.color.text_muted);

        switchNotifications.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                switchNotifications.setThumbTintList(ColorStateList.valueOf(colorActiveThumb));
                switchNotifications.setTrackTintList(ColorStateList.valueOf(colorActiveThumb));
                Toast.makeText(this, "Notifications ON", Toast.LENGTH_SHORT).show();
            } else {
                switchNotifications.setThumbTintList(ColorStateList.valueOf(colorMutedThumb));
                switchNotifications.setTrackTintList(ColorStateList.valueOf(colorMutedThumb));
                Toast.makeText(this, "Notifications OFF", Toast.LENGTH_SHORT).show();
            }
        });

        if (!switchNotifications.isChecked()) {
            switchNotifications.setThumbTintList(ColorStateList.valueOf(colorMutedThumb));
            switchNotifications.setTrackTintList(ColorStateList.valueOf(colorMutedThumb));
        }

        // 4. POP-UP INTERESE
        findViewById(R.id.btnMenuChangeInterests).setOnClickListener(v -> {
            BottomSheetDialog interestsDialog = new BottomSheetDialog(this);
            View intView = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_interests, null);
            interestsDialog.setContentView(intView);

            interestsDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

            popCards.clear();
            SharedPreferences sharedPref = getSharedPreferences("VoiajAppPrefs", Context.MODE_PRIVATE);
            Set<String> savedInterests = sharedPref.getStringSet("user_interests", new HashSet<>());
            selectedKeys.clear();
            selectedKeys.addAll(savedInterests);

            CardConfig[] configs = {
                    new CardConfig(R.id.cardBeaches, "beaches", "Beaches", "prelogin_img_1"),
                    new CardConfig(R.id.cardFood, "food", "Food", "prelogin_img_2"),
                    new CardConfig(R.id.cardCities, "historical", "Historical", "prelogin_img_3"),
                    new CardConfig(R.id.cardNature, "nature", "Nature", "prelogin_img_6"),
                    new CardConfig(R.id.cardCulture, "culture", "Culture", "prelogin_img_5"),
                    new CardConfig(R.id.cardMountains, "urbanism", "Urbanism", "prelogin_img_4")
            };

            MaterialButton btnSaveInterests = intView.findViewById(R.id.btnSaveInterests);

            for (CardConfig config : configs) {
                setupPopCard(intView, config.id, config.key, config.label, config.img, btnSaveInterests);
            }

            boolean isReady = selectedKeys.size() >= MIN_REQUIRED;
            btnSaveInterests.setEnabled(isReady);
            btnSaveInterests.setAlpha(isReady ? 1.0f : 0.5f);

            btnSaveInterests.setOnClickListener(sv -> {
                if (selectedKeys.size() < MIN_REQUIRED) return;
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putStringSet("user_interests", selectedKeys);
                editor.apply();
                interestsDialog.dismiss();
            });

            interestsDialog.show();
        });

        // 5. LOG OUT
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            Intent intent = new Intent(AccountSettingsActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
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