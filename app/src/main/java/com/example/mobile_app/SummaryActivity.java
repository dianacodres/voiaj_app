package com.example.mobile_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.util.HashSet;
import java.util.Set;

public class SummaryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        TextView tvSummary = findViewById(R.id.tvSummary);

        // Deschidem fișierul de preferințe locale
        SharedPreferences sharedPref = getSharedPreferences("VoiajAppPrefs", Context.MODE_PRIVATE);

        // Citim setul de string-uri salvat în InterestsActivity.
        // Dacă nu găsește nimic, va returna un HashSet gol ca fallback.
        Set<String> savedInterests = sharedPref.getStringSet("user_interests", new HashSet<>());

        // Afișăm textul final folosind numărul de elemente citite direct din memorie
        int count = savedInterests.size();
        tvSummary.setText(getString(R.string.summary_text, count));

        // OPȚIONAL: Dacă vrei să vezi în consolă ce interese exacte s-au salvat (ex: [beaches, food])
        System.out.println("Interesele salvate în memorie sunt: " + savedInterests.toString());

        findViewById(R.id.btnFinish).setOnClickListener(v -> finishAffinity());
    }
}