package com.example.mobile_app;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PoiAdapter extends RecyclerView.Adapter<PoiAdapter.VH> {

    interface OnSelectionChanged {
        void onChanged(String poiId, boolean isSelected);
    }

    private final List<PoiRow> items;
    private final Set<String> selectedIds;
    private final OnSelectionChanged listener;
    private final boolean isSelectionMode; // Variabila pentru a sti daca afisam sau nu cercul de bifat

    // Constructorul tau original (ramane intact pentru ecranele de selectie)
    public PoiAdapter(List<PoiRow> items, Set<String> selectedIds, OnSelectionChanged listener) {
        this.items = items;
        this.selectedIds = selectedIds;
        this.listener = listener;
        this.isSelectionMode = true;
    }

    // Constructor pentru ecranul „View All" (fara selectie)
    public PoiAdapter(List<PoiRow> items) {
        this.items = items;
        this.selectedIds = Collections.emptySet();
        this.listener = null;
        this.isSelectionMode = false;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_poi_row, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        PoiRow item = items.get(position);
        holder.name.setText(item.name);
        holder.subtitle.setText(item.subtitle);

        int imageResId = 0;
        if (item.id != null && !item.id.isEmpty()) {
            imageResId = holder.itemView.getResources()
                    .getIdentifier(item.id, "drawable", holder.itemView.getContext().getPackageName());
        }
        if (imageResId == 0) imageResId = R.drawable.rome;
        holder.img.setImageResource(imageResId);

        if (!isSelectionMode) {
            // "View all" - ascundem cerculetul de bifare si aratam in schimb iconita de bilet/info
            holder.circle.setVisibility(View.GONE);
            holder.checkIcon.setVisibility(View.GONE);

            if (item.url != null && !item.url.isEmpty()) {
                holder.linkBtn.setVisibility(View.VISIBLE);
                holder.linkBtn.setImageResource(item.isTicket ? R.drawable.ic_ticket : R.drawable.ic_info);
                holder.linkBtn.setOnClickListener(v -> {
                    try {
                        v.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.url)));
                    } catch (Exception ignored) {
                    }
                });
            } else {
                holder.linkBtn.setVisibility(View.GONE);
                holder.linkBtn.setOnClickListener(null);
            }
        } else {
            // Mod selectie (Plan a Trip) - ramane exact cum era, fara iconita de link
            holder.circle.setVisibility(View.VISIBLE);
            holder.linkBtn.setVisibility(View.GONE);
            boolean isSelected = selectedIds.contains(item.id);
            updateCircleState(holder, isSelected);

            holder.itemView.setOnClickListener(v -> {
                boolean nowSelected = !selectedIds.contains(item.id);
                if (nowSelected) {
                    selectedIds.add(item.id);
                } else {
                    selectedIds.remove(item.id);
                }
                updateCircleState(holder, nowSelected);
                if (listener != null) {
                    listener.onChanged(item.id, nowSelected);
                }
            });
        }
    }

    private void updateCircleState(VH holder, boolean isSelected) {
        holder.circle.setBackgroundResource(isSelected
                ? R.drawable.bg_selection_circle_selection
                : R.drawable.bg_selection_circle);
        holder.checkIcon.setVisibility(isSelected ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img, checkIcon, linkBtn;
        TextView name, subtitle;
        FrameLayout circle;

        VH(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.imgPoi);
            name = itemView.findViewById(R.id.txtPoiName);
            subtitle = itemView.findViewById(R.id.txtPoiSubtitle);
            circle = itemView.findViewById(R.id.circleSelect);
            checkIcon = itemView.findViewById(R.id.imgCheck);
            linkBtn = itemView.findViewById(R.id.btnPoiLink);
        }
    }
}