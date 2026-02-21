package com.webradio.navplus;

public interface StationListItem {
    class HeaderItem implements StationListItem {
        private final String city;

        public HeaderItem(String city) {
            this.city = city;
        }

        public String getCity() {
            return city;
        }
    }

    class StationItem implements StationListItem {
        private final ApiResponse.PlacePageResponse.StationPage station;

        public StationItem(ApiResponse.PlacePageResponse.StationPage station) {
            this.station = station;
        }

        public ApiResponse.PlacePageResponse.StationPage getStation() {
            return station;
        }
    }
}
