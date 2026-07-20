package com.example.mobile_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    interface OnTripClick {
        void onClick(Trip trip);
    }

    private final List<Trip> trips;
    private final boolean showCountdownBadge;
    private final OnTripClick clickListener;

    public TripAdapter(List<Trip> trips, boolean showCountdownBadge) {
        this(trips, showCountdownBadge, null);
    }

    public TripAdapter(List<Trip> trips, boolean showCountdownBadge, OnTripClick clickListener) {
        this.trips = trips;
        this.showCountdownBadge = showCountdownBadge;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip_card, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = trips.get(position);

        String countryLabel = trip.getCountryId() != null ? ", " + capitalize(trip.getCountryId()) : "";
        holder.txtTitle.setText(trip.getCityName() + countryLabel);
        holder.txtSubtitle.setText(formatSubtitle(trip));

        if (holder.imgTrip != null) {
            String rawUrl = trip.getImageUrl();

            if (rawUrl != null && (rawUrl.startsWith("http://") || rawUrl.startsWith("https://"))) {
                com.bumptech.glide.Glide.with(holder.itemView.getContext())
                        .load(rawUrl)
                        .dontAnimate()
                        .placeholder(R.drawable.rome)
                        .error(R.drawable.rome)
                        .into(holder.imgTrip);
            } else {
                int imageResId = 0;
                if (rawUrl != null && !rawUrl.trim().isEmpty()) {
                    String cleanName = rawUrl.replace(".jpg", "").replace(".png", "").trim().toLowerCase();
                    imageResId = holder.itemView.getResources()
                            .getIdentifier(cleanName, "drawable", holder.itemView.getContext().getPackageName());
                }

                if (imageResId == 0 && trip.getCityId() != null) {
                    String fallbackId = trip.getCityId().trim().toLowerCase();
                    imageResId = holder.itemView.getResources()
                            .getIdentifier(fallbackId, "drawable", holder.itemView.getContext().getPackageName());
                }

                if (imageResId == 0) {
                    imageResId = R.drawable.rome;
                }

                com.bumptech.glide.Glide.with(holder.itemView.getContext())
                        .load(imageResId)
                        .dontAnimate()
                        .into(holder.imgTrip);
            }
        }

        if (showCountdownBadge) {
            trip.recalculateCountdown();
            holder.layoutBadge.setVisibility(View.VISIBLE);

            if (trip.isOngoing()) {
                holder.txtBadgeDays.setText("");
                holder.txtBadgeLabel.setText("NOW");
            } else {
                holder.txtBadgeDays.setText(String.valueOf(trip.getDaysUntilTrip()));
                holder.txtBadgeLabel.setText("DAYS");
            }
        } else {
            holder.layoutBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(trip);
        });
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    private String formatSubtitle(Trip trip) {
        if (trip.getStartDate() == null || trip.getEndDate() == null) {
            String dayLabel = trip.getDaysCount() == 1 ? "day" : "days";
            return trip.getDaysCount() + "-" + dayLabel + " idea";
        }
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            SimpleDateFormat displayFormat = new SimpleDateFormat("MMM d", Locale.ENGLISH);
            String start = displayFormat.format(isoFormat.parse(trip.getStartDate()));
            String end = displayFormat.format(isoFormat.parse(trip.getEndDate()));
            return start + " - " + end + " · " + trip.getDaysCount() + " days";
        } catch (Exception e) {
            return trip.getDaysCount() + " days";
        }
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static class TripViewHolder extends RecyclerView.ViewHolder {
        ImageView imgTrip;
        TextView txtTitle, txtSubtitle, txtBadgeDays, txtBadgeLabel;
        LinearLayout layoutBadge;

        TripViewHolder(@NonNull View itemView) {
            super(itemView);
            imgTrip = itemView.findViewById(R.id.imgTripPhoto);
            txtTitle = itemView.findViewById(R.id.txtTripTitle);
            txtSubtitle = itemView.findViewById(R.id.txtTripSubtitle);
            layoutBadge = itemView.findViewById(R.id.layoutTripBadge);
            txtBadgeDays = itemView.findViewById(R.id.txtTripBadgeDays);
            txtBadgeLabel = itemView.findViewById(R.id.txtTripBadgeLabel);
        }
    }
}