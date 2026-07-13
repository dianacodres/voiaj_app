package com.example.mobile_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail;
    private EditText etPassword;

    private AuthRepository authRepository;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        authRepository = new AuthRepository();
        googleSignInClient = authRepository.getGoogleSignInClient(this);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Task<GoogleSignInAccount> task =
                            GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    handleGoogleSignInResult(task);
                }
        );

        etEmail = findViewById(R.id.edtEmail);
        etPassword = findViewById(R.id.edtPassword);

        PasswordToggleHelper.attach(etPassword);

        Button btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> attemptLogin());

        TextView txtStartExploringLink = findViewById(R.id.txtStartExploringLink);
        txtStartExploringLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });

        Button btnGoogle = findViewById(R.id.btnGoogle);
        btnGoogle.setOnClickListener(v -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });

        // ── FLUX PAROLĂ UITATĂ DIN ECRANUL DE LOGIN ──
        TextView txtForgotPassword = findViewById(R.id.txtForgotPassword);
        if (txtForgotPassword != null) {
            txtForgotPassword.setOnClickListener(v -> showResetPasswordDialog());
        }
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Please enter your email");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Please enter your password");
            etPassword.requestFocus();
            return;
        }

        authRepository.loginWithEmail(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(User user) {
                startActivity(new Intent(LoginActivity.this, DiscoverActivity.class));
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(LoginActivity.this,
                        "Authentication failed: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showResetPasswordDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.BottomSheetStyle);

        View sheetView = getLayoutInflater().inflate(R.layout.layout_bottom_sheet_forgot_password, null);
        bottomSheetDialog.setContentView(sheetView);
        bottomSheetDialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);

        EditText edtResetEmail = sheetView.findViewById(R.id.edtResetEmail);
        Button btnSendResetLink = sheetView.findViewById(R.id.btnSendResetLink);
        TextView txtCancelReset = sheetView.findViewById(R.id.txtCancelReset);

        // Dacă utilizatorul completase deja emailul în ecranul de login, îl punem direct în căsuță
        if (etEmail != null && !TextUtils.isEmpty(etEmail.getText().toString())) {
            edtResetEmail.setText(etEmail.getText().toString().trim());
        }

        btnSendResetLink.setOnClickListener(v -> {
            String email = edtResetEmail.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                edtResetEmail.setError("Please enter your email address");
                return;
            }

            btnSendResetLink.setEnabled(false); // Prevenim click-urile multiple

            // Trimitem emailul de resetare
            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        bottomSheetDialog.dismiss();
                        if (task.isSuccessful()) {
                            Toast.makeText(LoginActivity.this, "Password reset link sent! Check your inbox.", Toast.LENGTH_LONG).show();
                        } else {
                            String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                            Toast.makeText(LoginActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        txtCancelReset.setOnClickListener(v -> bottomSheetDialog.dismiss());

        bottomSheetDialog.show();
    }

    private void sendFirebaseResetEmail(String email) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "Password reset link sent! Check your inbox.", Toast.LENGTH_LONG).show();
                    } else {
                        String error = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(LoginActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null || account.getIdToken() == null) {
                Toast.makeText(this, "Google sign-in failed.", Toast.LENGTH_SHORT).show();
                return;
            }

            authRepository.firebaseAuthWithGoogle(
                    account.getIdToken(),
                    account.getDisplayName(),
                    account.getEmail(),
                    new AuthRepository.GoogleSignInCallback() {
                        @Override
                        public void onExistingUser(User user) {
                            startActivity(new Intent(LoginActivity.this, DiscoverActivity.class));
                            finish();
                        }

                        @Override
                        public void onNewUserNeedsInterests(User partialUser) {
                            Intent intent = new Intent(LoginActivity.this, InterestsActivity.class);
                            intent.putExtra("mode", "google_new");
                            intent.putExtra("uid", partialUser.getUid());
                            intent.putExtra("name", partialUser.getName());
                            intent.putExtra("email", partialUser.getEmail());
                            startActivity(intent);
                            finish();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Toast.makeText(LoginActivity.this,
                                    "Google Sign-In Error: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
            );
        } catch (ApiException e) {
            Toast.makeText(this, "Google Sign-In cancelled or failed.", Toast.LENGTH_SHORT).show();
        }
    }
}