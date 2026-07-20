package com.example.mobile_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class TripDraftAdapter extends RecyclerView.Adapter<TripDraftAdapter.DraftViewHolder> {

    interface OnDraftClick {
        void onClick(Trip trip);
    }

    interface OnDraftLongClick {
        void onLongClick(Trip trip);
    }

    private final List<Trip> drafts;
    private final OnDraftClick clickListener;
    private final OnDraftLongClick longClickListener;

    public TripDraftAdapter(List<Trip> drafts, OnDraftClick clickListener, OnDraftLongClick longClickListener) {
        this.drafts = drafts;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public DraftViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip_draft, parent, false);
        return new DraftViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DraftViewHolder holder, int position) {
        Trip trip = drafts.get(position);
        holder.txtTitle.setText(trip.getCityName());
        String dayLabel = trip.getDaysCount() == 1 ? "day" : "days";
        holder.txtSubtitle.setText(trip.getStartDate() == null
                ? trip.getDaysCount() + "-" + dayLabel + " idea"
                : trip.getDaysCount() + " " + dayLabel);

        // ── MODIFICAT RADICAL: Încărcare instantanee prin Glide pentru secțiunea de Drafts ──
        if (holder.imgDraft != null) {
            String imgName = trip.getImageUrl();

            if (imgName != null && (imgName.startsWith("http://") || imgName.startsWith("https://"))) {
                // Pas A: Imaginea este salvată ca URL web din Firestore
                com.bumptech.glide.Glide.with(holder.itemView.getContext())
                        .load(imgName)
                        .dontAnimate()
                        .placeholder(R.drawable.rome)
                        .error(R.drawable.rome)
                        .into(holder.imgDraft);
            } else {
                // Pas B: Căutare resursă în structura internă a folderului drawable
                int imageResId = 0;
                if (imgName != null && !imgName.trim().isEmpty()) {
                    imgName = imgName.replace(".jpg", "").replace(".png", "").trim().toLowerCase();
                    imageResId = holder.itemView.getResources()
                            .getIdentifier(imgName, "drawable", holder.itemView.getContext().getPackageName());
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
                        .into(holder.imgDraft);
            }
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onLongClick(trip);
            return true;
        });

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(trip);
        });
    }

    @Override
    public int getItemCount() {
        return drafts.size();
    }

    static class DraftViewHolder extends RecyclerView.ViewHolder {
        ImageView imgDraft;
        TextView txtTitle, txtSubtitle;

        DraftViewHolder(@NonNull View itemView) {
            super(itemView);
            imgDraft = itemView.findViewById(R.id.imgDraftPhoto);
            txtTitle = itemView.findViewById(R.id.txtDraftTitle);
            txtSubtitle = itemView.findViewById(R.id.txtDraftSubtitle);
        }
    }
}