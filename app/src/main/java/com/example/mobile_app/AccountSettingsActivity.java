package com.example.mobile_app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;

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
    private User currentUser;

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

        // 1. Edge to Edge și setare layout
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile_settings);

        // 2. Modelul tău funcțional pentru transparență
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }

        View rootLayout = findViewById(R.id.mainRoot);
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                // Doar padding jos pentru navigare
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, 0, 0, systemBars.bottom);
                return insets;
            });
        }

        View headerView = findViewById(R.id.headerBackground); 
        if (headerView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(headerView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                int extraPadding = (int) (20 * getResources().getDisplayMetrics().density);
                // Padding total sus: status bar + 20dp
                v.setPadding(v.getPaddingLeft(), systemBars.top + extraPadding, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        txtUserName = findViewById(R.id.txtUserName);
        txtUserEmail = findViewById(R.id.txtUserEmail);

        authRepository = new AuthRepository();
        loadCurrentUserProfile();

        // 1. EDIT PROFILE BOTTOM SHEET
        findViewById(R.id.btnEditProfile).setOnClickListener(v -> {
            BottomSheetDialog profileDialog = new BottomSheetDialog(this);
            View view = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_edit_profile, null);
            profileDialog.setContentView(view);
            profileDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

            EditText edtPopName = view.findViewById(R.id.edtPopName);
            EditText edtPopEmail = view.findViewById(R.id.edtPopEmail);
            edtPopName.setText(txtUserName.getText().toString());
            edtPopEmail.setText(txtUserEmail.getText().toString());
            edtPopEmail.setEnabled(false);

            view.findViewById(R.id.btnDiscardProfile).setOnClickListener(db -> profileDialog.dismiss());
            view.findViewById(R.id.btnSaveProfile).setOnClickListener(sb -> {
                String newName = edtPopName.getText().toString().trim();
                if (TextUtils.isEmpty(newName)) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (currentUser == null) {
                    Toast.makeText(this, "Profile not loaded yet.", Toast.LENGTH_SHORT).show();
                    return;
                }

                authRepository.updateName(currentUser.getUid(), newName, new AuthRepository.AuthCallback() {
                    @Override
                    public void onSuccess(User user) {
                        currentUser = user;
                        txtUserName.setText(user.getName());
                        TextView txtAvatar = findViewById(R.id.txtAvatar);
                        if (txtAvatar != null && user.getAvatarInitials() != null) {
                            txtAvatar.setText(user.getAvatarInitials());
                        }
                        profileDialog.dismiss();
                        Toast.makeText(AccountSettingsActivity.this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(AccountSettingsActivity.this,
                                "Error saving: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            });

            profileDialog.show();
        });

        // 2. CHANGE PASSWORD BOTTOM SHEET
        findViewById(R.id.btnMenuChangePassword).setOnClickListener(v -> {
            if (currentUser != null && "google".equals(currentUser.getAuthProvider())) {
                Toast.makeText(this,
                        "Your account is connected via Google. Password must be managed inside your Google Account.",
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

            PasswordToggleHelper.attach(edtCurrentPassword);
            PasswordToggleHelper.attach(edtNewPassword);

            btnSubmit.setOnClickListener(view -> {
                String currentPassword = edtCurrentPassword.getText().toString();
                String newPassword = edtNewPassword.getText().toString();

                if (TextUtils.isEmpty(currentPassword)) {
                    edtCurrentPassword.setError("Please enter your current password");
                    return;
                }
                if (TextUtils.isEmpty(newPassword) || newPassword.length() < 6) {
                    edtNewPassword.setError("New password must be at least 6 characters long");
                    return;
                }

                btnSubmit.setEnabled(false);
                authRepository.changePassword(currentPassword, newPassword, new AuthRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(AccountSettingsActivity.this, "Password updated successfully", Toast.LENGTH_SHORT).show();
                        passwordDialog.dismiss();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        btnSubmit.setEnabled(true);
                        Toast.makeText(AccountSettingsActivity.this,
                                "Couldn't change password: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            });

            // ── IMPLEMENTARE CORECTĂ PENTRU PAROLA UITATĂ DIN POP-UP ──
            passView.findViewById(R.id.txtPopUpForgotPassword).setOnClickListener(view -> {
                passwordDialog.dismiss();
                if (currentUser != null && currentUser.getEmail() != null) {
                    FirebaseAuth.getInstance().sendPasswordResetEmail(currentUser.getEmail())
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Toast.makeText(AccountSettingsActivity.this, "A secure password reset link has been sent to " + currentUser.getEmail(), Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(AccountSettingsActivity.this, "Failed to send reset link.", Toast.LENGTH_SHORT).show();
                                }
                            });
                }
            });
            passwordDialog.show();
        });

        // 3. NOTIFICATIONS
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

        // 4. EDIT INTERESTS BOTTOM SHEET
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

            CardConfig[] configs = {
                    new CardConfig(R.id.cardBeaches, "beaches", "Beaches", "prelogin_img_1"),
                    new CardConfig(R.id.cardFood, "food", "Food", "prelogin_img_2"),
                    new CardConfig(R.id.cardCities, "historical", "Historical", "prelogin_img_3"),
                    new CardConfig(R.id.cardMountains, "urbanism", "Urbanism", "prelogin_img_4"),
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
                                Toast.makeText(AccountSettingsActivity.this, "Interests updated successfully", Toast.LENGTH_SHORT).show();
                                interestsDialog.dismiss();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                btnSaveInterests.setEnabled(true);
                                Toast.makeText(AccountSettingsActivity.this,
                                        "Error: " + errorMessage, Toast.LENGTH_LONG).show();
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

        findViewById(R.id.itemHelp).setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Help & Support")
                    .setMessage("Contact the creators for help:\nagachedianaa@gmail.com\ncodresdiana@gmail.com")
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    private void loadCurrentUserProfile() {
        authRepository.getCurrentUserProfile(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(User user) {
                currentUser = user;
                txtUserName.setText(user.getName());
                txtUserEmail.setText(user.getEmail());

                TextView txtAvatar = findViewById(R.id.txtAvatar);
                if (txtAvatar != null && user.getAvatarInitials() != null) {
                    txtAvatar.setText(user.getAvatarInitials());
                }

                TextView txtCompletedTripsCount = findViewById(R.id.txtCompletedTripsCount);
                TextView txtSavedTripsCount = findViewById(R.id.txtSavedTripsCount);
                if (txtCompletedTripsCount != null) {
                    txtCompletedTripsCount.setText(String.valueOf(user.getCompletedTripsCount()));
                }
                if (txtSavedTripsCount != null) {
                    txtSavedTripsCount.setText(String.valueOf(user.getSavedTripsCount()));
                }
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(AccountSettingsActivity.this,
                        "Couldn't load profile: " + errorMessage, Toast.LENGTH_LONG).show();
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