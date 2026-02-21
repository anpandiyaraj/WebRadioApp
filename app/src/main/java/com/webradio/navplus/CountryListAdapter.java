package com.webradio.navplus;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class CountryListAdapter extends RecyclerView.Adapter<CountryListAdapter.ViewHolder> {

    public interface OnCountryClickListener {
        void onCountryClick(String country);
    }

    private final List<String> countries;
    private final OnCountryClickListener listener;
    private String activeCountry = null;

    public CountryListAdapter(List<String> countries, OnCountryClickListener listener) {
        this.countries = countries;
        this.listener = listener;
    }

    public void setActiveCountry(String country) {
        activeCountry = country;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_country, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String country = countries.get(position);
        holder.tvCountry.setText(country);

        boolean isActive = country.equals(activeCountry);
        holder.tvCountry.setTextColor(holder.tvCountry.getContext().getColor(
                isActive ? R.color.accent : R.color.text_secondary));
        holder.itemView.setBackgroundColor(holder.itemView.getContext().getColor(
                isActive ? R.color.card_active : android.R.color.transparent));

        holder.itemView.setOnClickListener(v -> {
            activeCountry = country;
            notifyDataSetChanged();
            listener.onCountryClick(country);
        });
    }

    @Override
    public int getItemCount() {
        return countries.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCountry;
        ViewHolder(View view) {
            super(view);
            tvCountry = view.findViewById(R.id.tv_country_name);
        }
    }
}
