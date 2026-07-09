package com.example.mobile_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Adapter pentru bottom sheet-ul de selectare a destinatiei, cu headere pe tara. */
public class CityPickerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface OnCitySelectedListener {
        void onCitySelected(City city);
    }

    private final List<CityListRow> rows;
    private final OnCitySelectedListener listener;

    public CityPickerAdapter(List<CityListRow> rows, OnCitySelectedListener listener) {
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
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_city_row, parent, false);
            return new CityViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        CityListRow row = rows.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).txtHeader.setText(row.headerText);
        } else if (holder instanceof CityViewHolder) {
            CityViewHolder cityHolder = (CityViewHolder) holder;
            cityHolder.txtCityName.setText(row.city.getName());
            cityHolder.itemView.setOnClickListener(v -> listener.onCitySelected(row.city));
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView txtHeader;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            txtHeader = (TextView) itemView;
        }
    }

    static class CityViewHolder extends RecyclerView.ViewHolder {
        TextView txtCityName;

        CityViewHolder(@NonNull View itemView) {
            super(itemView);
            txtCityName = (TextView) itemView;
        }
    }
}