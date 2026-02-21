package com.webradio.navplus;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

public class StationAdapter extends ListAdapter<StationListItem, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_STATION = 1;

    public interface OnStationClickListener {
        void onStationClick(ApiResponse.PlacePageResponse.StationPage station);
    }

    private final OnStationClickListener listener;
    private int activePosition = -1;

    public StationAdapter(OnStationClickListener listener) {
        super(new DiffUtil.ItemCallback<StationListItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull StationListItem oldItem, @NonNull StationListItem newItem) {
                boolean isOldHeader = oldItem instanceof StationListItem.HeaderItem;
                boolean isNewHeader = newItem instanceof StationListItem.HeaderItem;
                if (isOldHeader && isNewHeader) {
                    return ((StationListItem.HeaderItem) oldItem).getCity().equals(((StationListItem.HeaderItem) newItem).getCity());
                }

                boolean isOldStation = oldItem instanceof StationListItem.StationItem;
                boolean isNewStation = newItem instanceof StationListItem.StationItem;
                if (isOldStation && isNewStation) {
                    ApiResponse.PlacePageResponse.StationPage oldStation = ((StationListItem.StationItem) oldItem).getStation();
                    ApiResponse.PlacePageResponse.StationPage newStation = ((StationListItem.StationItem) newItem).getStation();
                    return oldStation.title.equals(newStation.title);
                }

                return false;
            }

            @Override
            public boolean areContentsTheSame(@NonNull StationListItem oldItem, @NonNull StationListItem newItem) {
                boolean isOldHeader = oldItem instanceof StationListItem.HeaderItem;
                boolean isNewHeader = newItem instanceof StationListItem.HeaderItem;
                if (isOldHeader && isNewHeader) {
                    return ((StationListItem.HeaderItem) oldItem).getCity().equals(((StationListItem.HeaderItem) newItem).getCity());
                }

                boolean isOldStation = oldItem instanceof StationListItem.StationItem;
                boolean isNewStation = newItem instanceof StationListItem.StationItem;
                if (isOldStation && isNewStation) {
                    ApiResponse.PlacePageResponse.StationPage oldStation = ((StationListItem.StationItem) oldItem).getStation();
                    ApiResponse.PlacePageResponse.StationPage newStation = ((StationListItem.StationItem) newItem).getStation();
                    return oldStation.url.equals(newStation.url);
                }

                return false;
            }
        });
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position) instanceof StationListItem.HeaderItem ? VIEW_TYPE_HEADER : VIEW_TYPE_STATION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_city_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_station, parent, false);
            return new StationViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_HEADER) {
            HeaderViewHolder headerViewHolder = (HeaderViewHolder) holder;
            StationListItem.HeaderItem headerItem = (StationListItem.HeaderItem) getItem(position);
            headerViewHolder.cityHeader.setText(headerItem.getCity());
        } else {
            StationViewHolder stationViewHolder = (StationViewHolder) holder;
            StationListItem.StationItem stationItem = (StationListItem.StationItem) getItem(position);
            ApiResponse.PlacePageResponse.StationPage station = stationItem.getStation();
            stationViewHolder.stationName.setText(station.title);
            stationViewHolder.stationCity.setText(station.place != null ? station.place.title : "");

            boolean isActive = position == activePosition;
            stationViewHolder.card.setCardBackgroundColor(
                    stationViewHolder.card.getContext().getColor(isActive ? R.color.card_active : R.color.card_bg));
            stationViewHolder.stationName.setTextColor(
                    stationViewHolder.card.getContext().getColor(isActive ? R.color.accent : R.color.text_primary));

            stationViewHolder.card.setOnClickListener(v -> {
                int prev = activePosition;
                activePosition = holder.getAdapterPosition();
                notifyItemChanged(prev);
                notifyItemChanged(activePosition);
                listener.onStationClick(station);
            });
        }
    }

    public void setActivePosition(int position) {
        int prev = activePosition;
        activePosition = position;
        notifyItemChanged(prev);
        notifyItemChanged(activePosition);
    }

    public static class StationViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        TextView stationName;
        TextView stationCity;

        StationViewHolder(View view) {
            super(view);
            card = view.findViewById(R.id.card_station);
            stationName = view.findViewById(R.id.tv_station_name);
            stationCity = view.findViewById(R.id.tv_station_city);
        }
    }

    public static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView cityHeader;

        HeaderViewHolder(View view) {
            super(view);
            cityHeader = view.findViewById(R.id.tv_city_header);
        }
    }
}
