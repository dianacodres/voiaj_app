package com.example.mobile_app;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.regex.Pattern;

/**
 * Pasul 1/2 din onboarding — Creare cont.
 */
public class SignUpActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView tvStepCount;
    private Button btnContinue;

    // Înlocuit din TextInputLayout/TextInputEditText în EditText simplu conform noului XML
    private EditText etName, etEmail, etPassword, etConfirm;

    // Regex pentru nume: acceptă doar litere (inclusiv diacritice), spații și cratime (-)
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-ZăâîșțĂÂÎȘȚ\\s-]+$");

    // Regex strict pentru email corporativ/standard (asigură structură completă x@y.z)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        progressBar = findViewById(R.id.progressBar);
        tvStepCount = findViewById(R.id.tvStepCount);
        btnContinue = findViewById(R.id.btnContinue);

        // Setează progresul inițial pentru pasul 1
        progressBar.setProgress(50);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(SignUpActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        // Inițializare noile componente EditText
        etName     = findViewById(R.id.etName);
        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirm  = findViewById(R.id.etConfirm);

        PasswordToggleHelper.attach(etPassword);
        PasswordToggleHelper.attach(etConfirm);

        // Șterge eroarea vizuală când utilizatorul reîncepe să scrie
        addClearErrorWatcher(etName);
        addClearErrorWatcher(etEmail);
        addClearErrorWatcher(etPassword);
        addClearErrorWatcher(etConfirm);

        // IMPORTANT: Un singur Listener! Validarea pornește de aici.
        btnContinue.setOnClickListener(v -> attemptContinue());

        // Link „Ai deja cont?" → navighează la LoginActivity
        TextView tvSignIn = findViewById(R.id.tvSignIn);
        tvSignIn.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void attemptContinue() {
        // Resetăm erorile vizuale anterioare
        etName.setError(null);
        etEmail.setError(null);
        etPassword.setError(null);
        etConfirm.setError(null);

        String name     = etName.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString(); // Fără trim pentru parole (spațiile pot face parte din parolă)
        String confirm  = etConfirm.getText().toString();

        boolean valid = true;

        // 1. Validare Nume complet (doar litere, spații și cratime)
        if (name.isEmpty()) {
            etName.setError("Introduceți numele complet");
            valid = false;
        } else if (!NAME_PATTERN.matcher(name).matches()) {
            etName.setError("Numele poate conține doar litere, spații și cratima (-)");
            valid = false;
        }

        // 2. Validare Email cu structură fixă cunoscută
        if (email.isEmpty()) {
            etEmail.setError("Introduceți adresa de email");
            valid = false;
        } else if (!EMAIL_PATTERN.matcher(email).matches()) {
            etEmail.setError("Adresă de email invalidă (ex: exemplu@domeniu.com)");
            valid = false;
        }

        // 3. Validare Parolă (minim 7 caractere, cel puțin o cifră sau un simbol)
        if (password.isEmpty()) {
            etPassword.setError("Introduceți parola");
            valid = false;
        } else if (password.length() < 7) {
            etPassword.setError("Parola trebuie să aibă cel puțin 7 caractere");
            valid = false;
        } else if (!containsDigitOrSymbol(password)) {
            etPassword.setError("Parola trebuie să conțină cel puțin o cifră sau un simbol");
            valid = false;
        }

        // 4. Validare Confirmare Parolă
        if (confirm.isEmpty()) {
            etConfirm.setError("Confirmați parola");
            valid = false;
        } else if (!confirm.equals(password)) {
            etConfirm.setError("Parolele nu coincid");
            valid = false;
        }

        if (!valid) {
            Toast.makeText(this, "Te rugăm să corectezi erorile afișate.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Dacă totul este valid, trecem la animație și trimitem datele mai departe
        animateToNextStep();
    }

    /**
     * Verifică dacă textul conține cel puțin o cifră sau un caracter special/simbol
     */
    private boolean containsDigitOrSymbol(String s) {
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c) || (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
                return true;
            }
        }
        return false;
    }

    private void animateToNextStep() {
        btnContinue.setEnabled(false);

        ObjectAnimator anim = ObjectAnimator.ofInt(progressBar, "progress", 50, 100);
        anim.setDuration(350);
        anim.start();
        tvStepCount.setText("2/2");

        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        progressBar.postDelayed(() -> {
            Intent intent = new Intent(SignUpActivity.this, InterestsActivity.class);
            intent.putExtra("name", name);
            intent.putExtra("email", email);
            intent.putExtra("password", password);
            startActivity(intent);
            btnContinue.setEnabled(true);
        }, 400);
    }

    private void addClearErrorWatcher(EditText et) {
        et.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                et.setError(null); // Șterge iconița de eroare în timp ce tastează
            }
            public void afterTextChanged(Editable s) {}
        });
    }
}