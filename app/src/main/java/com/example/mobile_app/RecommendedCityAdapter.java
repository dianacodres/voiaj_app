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

/** Adapter pentru RecommendedActivity - o lista verticala completa, sortata dupa % match. */
public class RecommendedCityAdapter extends RecyclerView.Adapter<RecommendedCityAdapter.VH> {

    interface OnCityClick {
        void onClick(City city);
    }

    private final List<RecommendationEngine.CityMatch> matches;
    private final OnCityClick listener;

    public RecommendedCityAdapter(List<RecommendationEngine.CityMatch> matches, OnCityClick listener) {
        this.matches = matches;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommended_city, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        RecommendationEngine.CityMatch match = matches.get(position);
        City city = match.city;

        holder.name.setText(city.getName());

        double ratingValue = city.getRating() != null ? city.getRating() : 0.0;
        holder.rating.setText(String.format(Locale.US, "⭐ %.1f", ratingValue));

        holder.matchText.setText(capitalize(city.getCountryId()) + " · " + match.matchPercent + "% match");

        // ── INJECTARE GLIDE: Încărcare dinamică din Firestore sau Drawable cu fallback total pe Roma ──
        if (holder.img != null) {
            String rawUrl = null;
            try {
                rawUrl = city.getImageUrl();
            } catch (Exception ignored) {}

            if (rawUrl != null && (rawUrl.startsWith("http://") || rawUrl.startsWith("https://"))) {
                // Pas A: Este link web extern de pe internet (ex: Florence)
                com.bumptech.glide.Glide.with(holder.itemView.getContext())
                        .load(rawUrl)
                        .placeholder(R.drawable.rome)
                        .error(R.drawable.rome)
                        .into(holder.img);
            } else {
                // Pas B: Este o denumire de resursă locală în string sau fallback pe ID-ul orașului
                int imageResId = 0;
                if (rawUrl != null && !rawUrl.trim().isEmpty()) {
                    String cleanName = rawUrl.replace(".jpg", "").replace(".png", "").trim().toLowerCase();
                    imageResId = holder.itemView.getResources()
                            .getIdentifier(cleanName, "drawable", holder.itemView.getContext().getPackageName());
                }

                if (imageResId == 0 && city.getId() != null) {
                    String fallbackId = city.getId().trim().toLowerCase();
                    imageResId = holder.itemView.getResources()
                            .getIdentifier(fallbackId, "drawable", holder.itemView.getContext().getPackageName());
                }

                if (imageResId == 0) {
                    imageResId = R.drawable.rome;
                }

                com.bumptech.glide.Glide.with(holder.itemView.getContext())
                        .load(imageResId)
                        .into(holder.img);
            }
        }

        holder.itemView.setOnClickListener(v -> listener.onClick(city));
    }

    @Override
    public int getItemCount() {
        return matches.size();
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img;
        TextView name, rating, matchText;

        VH(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.imgRecCity);
            name = itemView.findViewById(R.id.txtRecName);
            rating = itemView.findViewById(R.id.txtRecRating);
            matchText = itemView.findViewById(R.id.txtRecMatch);
        }
    }
}