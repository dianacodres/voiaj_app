package com.example.mobile_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    // Model simplu pentru oras
    static class City {
        String name, region;
        float rating;
        int imageRes;
        City(String name, String region, float rating, int imageRes) {
            this.name = name; this.region = region;
            this.rating = rating; this.imageRes = imageRes;
        }
    }

    // Date hardcodate per mood
    private List<City> getHistoryCities() {
        List<City> list = new ArrayList<>();
        list.add(new City("Rome", "Lazio · Capital", 4.8f, R.drawable.roma));
        list.add(new City("Florence", "Tuscany", 4.8f, R.drawable.roma));
        list.add(new City("Venice", "Veneto", 4.7f, R.drawable.roma));
        list.add(new City("Milan", "Lombardy", 4.6f, R.drawable.roma));
        return list;
    }

    private List<City> getBeachCities() {
        List<City> list = new ArrayList<>();
        list.add(new City("Amalfi", "Campania", 4.9f, R.drawable.roma));
        list.add(new City("Sardinia", "Island", 4.8f, R.drawable.roma));
        list.add(new City("Sicily", "Island", 4.7f, R.drawable.roma));
        return list;
    }

    private List<City> getFoodCities() {
        List<City> list = new ArrayList<>();
        list.add(new City("Naples", "Campania", 4.7f, R.drawable.roma));
        list.add(new City("Bologna", "Emilia-Romagna", 4.8f, R.drawable.roma));
        list.add(new City("Rome", "Lazio · Capital", 4.8f, R.drawable.roma));
        return list;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.searchRoot), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(0, systemBars.top, 0, systemBars.bottom);
                    return insets;
                });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Click pe mood cards
        findViewById(R.id.moodHistory).setOnClickListener(v ->
                showMoodBottomSheet("History & Culture", "4 destinations", getHistoryCities()));
        findViewById(R.id.moodBeach).setOnClickListener(v ->
                showMoodBottomSheet("Sun & Beach", "3 destinations", getBeachCities()));
        findViewById(R.id.moodFood).setOnClickListener(v ->
                showMoodBottomSheet("Food & Wine", "3 destinations", getFoodCities()));
        findViewById(R.id.moodArt).setOnClickListener(v ->
                showMoodBottomSheet("Art & Museums", "3 destinations", getHistoryCities()));
        findViewById(R.id.moodNature).setOnClickListener(v ->
                showMoodBottomSheet("Nature & Hiking", "3 destinations", getBeachCities()));
        findViewById(R.id.moodBudget).setOnClickListener(v ->
                showMoodBottomSheet("Budget Travel", "3 destinations", getFoodCities()));
    }

    private void showMoodBottomSheet(String title, String subtitle, List<City> cities) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.BottomSheetStyle);
        View view = LayoutInflater.from(this)
                .inflate(R.layout.fragment_mood_bottom_sheet, null);

        ((TextView) view.findViewById(R.id.txtMoodTitle)).setText(title);
        ((TextView) view.findViewById(R.id.txtMoodSub)).setText(subtitle);

        RecyclerView rv = view.findViewById(R.id.rvCities);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(new CityAdapter(cities, city -> {
            dialog.dismiss();
            startActivity(new Intent(this, CityDetailActivity.class));
        }));

        dialog.setContentView(view);
        dialog.show();
    }

    // Adapter simplu pentru RecyclerView
    static class CityAdapter extends RecyclerView.Adapter<CityAdapter.VH> {
        interface OnCityClick { void onClick(City city); }
        List<City> cities;
        OnCityClick listener;

        CityAdapter(List<City> cities, OnCityClick listener) {
            this.cities = cities;
            this.listener = listener;
        }

        static class VH extends RecyclerView.ViewHolder {
            TextView name, region, rating;
            android.widget.ImageView img;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.txtCityName);
                region = v.findViewById(R.id.txtCityRegion);
                rating = v.findViewById(R.id.txtRating);
                img = v.findViewById(R.id.imgCity);
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_city_bottom_sheet, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            City city = cities.get(position);
            holder.name.setText(city.name);
            holder.region.setText(city.region);
            holder.rating.setText("★ " + city.rating);
            holder.img.setImageResource(city.imageRes);
            holder.itemView.setOnClickListener(v -> listener.onClick(city));
        }

        @Override
        public int getItemCount() { return cities.size(); }
    }
}