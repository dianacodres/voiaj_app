package com.example.mobile_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

/**
 * Adapter pentru lista de orase dintr-un mood (History, Sun & Beach etc.), grupata pe tara,
 * cu poza + rating + nume pe fiecare oras - la fel ca inainte, dar acum cu headere de tara.
 */
public class MoodCityAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface OnCityClick {
        void onClick(City city);
    }

    private final List<CityListRow> rows;
    private final OnCityClick listener;

    public MoodCityAdapter(List<CityListRow> rows, OnCityClick listener) {
        this.rows = rows;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == CityListRow.TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_city_header, parent, false);
            return new HeaderVH(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_city_bottom_sheet, parent, false);
            return new CityVH(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        CityListRow row = rows.get(position);
        if (holder instanceof HeaderVH) {
            ((HeaderVH) holder).txtHeader.setText(row.headerText);
        } else if (holder instanceof CityVH) {
            CityVH cityHolder = (CityVH) holder;
            City city = row.city;
            cityHolder.name.setText(city.getName());

            String subtitle = city.getTagline() != null && !city.getTagline().isEmpty()
                    ? city.getTagline()
                    : capitalize(city.getCountryId());
            cityHolder.region.setText(subtitle);

            double ratingValue = city.getRating() != null ? city.getRating() : 0.0;
            cityHolder.rating.setText(String.format(Locale.US, "★ %.1f", ratingValue));

            int imageResId = cityHolder.itemView.getResources()
                    .getIdentifier(city.getId(), "drawable", cityHolder.itemView.getContext().getPackageName());
            if (imageResId == 0) {
                imageResId = R.drawable.rome;
            }
            cityHolder.img.setImageResource(imageResId);

            cityHolder.itemView.setOnClickListener(v -> listener.onClick(city));
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView txtHeader;

        HeaderVH(@NonNull View itemView) {
            super(itemView);
            txtHeader = (TextView) itemView;
        }
    }

    static class CityVH extends RecyclerView.ViewHolder {
        TextView name, region, rating;
        ImageView img;

        CityVH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.txtCityName);
            region = itemView.findViewById(R.id.txtCityRegion);
            rating = itemView.findViewById(R.id.txtRating);
            img = itemView.findViewById(R.id.imgCity);
        }
    }
}