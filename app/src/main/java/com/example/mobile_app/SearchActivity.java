package com.example.mobile_app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private CityRepository cityRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search);

        // --- 1. SETARE TRANSPARENȚĂ BARĂ STATUS ---
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        // Forțăm pictogramele să fie albe (sau dark, depinde de background-ul din XML)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.getInsetsController().setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        // --- 2. AJUSTARE INSETS ---
        // În loc de padding sus, lăsăm layout-ul să urce sub bară
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.searchRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Padding doar jos pentru navigare
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        // --- 3. PADDING PENTRU HEADER (Pentru a nu suprapune textul cu ceasul) ---
        // Dacă ai un header/container sus în activity_search.xml, aplică padding-ul acolo
        View header = findViewById(R.id.header); // Asigură-te că ai un ID pe containerul de sus
        if (header != null) {
            ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        cityRepository = new CityRepository();
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.moodHistory).setOnClickListener(v ->
                loadMoodCities("History & Culture", "historical"));
        findViewById(R.id.moodBeach).setOnClickListener(v ->
                loadMoodCities("Sun & Beach", "sun&beach"));
        findViewById(R.id.moodFood).setOnClickListener(v ->
                loadMoodCities("Food & Wine", "food&wine"));
        findViewById(R.id.moodArt).setOnClickListener(v ->
                loadMoodCitiesAny("Art & Museums", Arrays.asList("art", "museums")));
        findViewById(R.id.moodNature).setOnClickListener(v ->
                loadMoodCities("Nature", "nature"));
        findViewById(R.id.moodBudget).setOnClickListener(v ->
                loadMoodCities("Budget Travel", "budget"));
    }

    private void loadMoodCities(String title, String keyword) {
        cityRepository.getCitiesByKeyword(keyword, new CityRepository.CityListCallback() {
            @Override
            public void onSuccess(List<City> cities) {
                showMoodBottomSheet(title, cities);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(SearchActivity.this,
                        "Could not load cities: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadMoodCitiesAny(String title, List<String> keywords) {
        cityRepository.getCitiesByAnyKeyword(keywords, new CityRepository.CityListCallback() {
            @Override
            public void onSuccess(List<City> cities) {
                showMoodBottomSheet(title, cities);
            }

            @Override
            public void onError(String errorMessage) {
                Toast.makeText(SearchActivity.this,
                        "Could not load cities: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showMoodBottomSheet(String title, List<City> cities) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this)
                .inflate(R.layout.fragment_mood_bottom_sheet, null);

        ((TextView) view.findViewById(R.id.txtMoodTitle)).setText(title);
        ((TextView) view.findViewById(R.id.txtMoodSub)).setText(
                cities.size() + (cities.size() == 1 ? " destination" : " destinations"));

        List<City> sorted = new ArrayList<>(cities);
        Collections.sort(sorted, (a, b) -> {
            int countryCompare = capitalize(a.getCountryId()).compareToIgnoreCase(capitalize(b.getCountryId()));
            if (countryCompare != 0) return countryCompare;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        List<CityListRow> rows = new ArrayList<>();
        String lastCountry = null;
        for (City city : sorted) {
            String countryLabel = capitalize(city.getCountryId());
            if (!countryLabel.equals(lastCountry)) {
                rows.add(CityListRow.header(countryLabel));
                lastCountry = countryLabel;
            }
            rows.add(CityListRow.cityRow(city));
        }

        RecyclerView rv = view.findViewById(R.id.rvCities);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new MoodCityAdapter(rows, city -> {
            dialog.dismiss();
            Intent intent = new Intent(this, CityDetailActivity.class);
            intent.putExtra("cityId", city.getId());
            intent.putExtra("cityName", city.getName());
            intent.putExtra("countryId", city.getCountryId());
            startActivity(intent);
        }));

        dialog.setContentView(view);
        dialog.show();
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}