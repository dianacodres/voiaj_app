package com.example.mobile_app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

public class LoginActivity extends AppCompatActivity {

    // TODO: verifica sa corespunda EXACT cu id-urile din activity_login.xml
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

        // Se inregistreaza inainte de a fi folosit, o singura data
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

        // Iconita de ochi (ic_eye) devine functionala - arata/ascunde parola la click
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
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Introdu emailul");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Introdu parola");
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
                        "Autentificare esuata: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null || account.getIdToken() == null) {
                Toast.makeText(this, "Autentificare Google esuata.", Toast.LENGTH_SHORT).show();
                return;
            }

            authRepository.firebaseAuthWithGoogle(
                    account.getIdToken(),
                    account.getDisplayName(),
                    account.getEmail(),
                    new AuthRepository.GoogleSignInCallback() {
                        @Override
                        public void onExistingUser(User user) {
                            // Cont existent -> direct in aplicatie
                            startActivity(new Intent(LoginActivity.this, DiscoverActivity.class));
                            finish();
                        }

                        @Override
                        public void onNewUserNeedsInterests(User partialUser) {
                            // Prima logare cu Google -> Google nu ne da interese,
                            // il trimitem la ecranul de selectare, la fel ca la signup cu email.
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
                                    "Eroare Google Sign-In: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
            );
        } catch (ApiException e) {
            Toast.makeText(this, "Google Sign-In anulat sau esuat.", Toast.LENGTH_SHORT).show();
        }
    }
}