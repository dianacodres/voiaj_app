package com.example.mobile_app;

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
    private final boolean isSelectionMode; // Variabilă pentru a ști dacă afișăm sau nu cercul de bifat

    // Constructorul tău original (rămâne intact pentru ecranele de selecție)
    public PoiAdapter(List<PoiRow> items, Set<String> selectedIds, OnSelectionChanged listener) {
        this.items = items;
        this.selectedIds = selectedIds;
        this.listener = listener;
        this.isSelectionMode = true;
    }

    // Constructor NOU: special pentru ecranul „View All” (fără selecție)
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

        // Dacă nu suntem în modul de selecție, ascundem complet cercul din dreapta
        if (!isSelectionMode) {
            holder.circle.setVisibility(View.GONE);
            holder.checkIcon.setVisibility(View.GONE);
        } else {
            holder.circle.setVisibility(View.VISIBLE);
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
        ImageView img, checkIcon;
        TextView name, subtitle;
        FrameLayout circle;

        VH(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.imgPoi);
            name = itemView.findViewById(R.id.txtPoiName);
            subtitle = itemView.findViewById(R.id.txtPoiSubtitle);
            circle = itemView.findViewById(R.id.circleSelect);
            checkIcon = itemView.findViewById(R.id.imgCheck);
        }
    }
}