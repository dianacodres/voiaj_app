package com.example.mobile_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Adapter pentru cardurile mici de Drafts, afisate orizontal (glisabile). */
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

        int imageResId = holder.itemView.getResources()
                .getIdentifier(trip.getCityId(), "drawable", holder.itemView.getContext().getPackageName());
        if (imageResId == 0) {
            imageResId = R.drawable.interests_prelogin_img_1;
        }
        holder.imgDraft.setImageResource(imageResId);

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