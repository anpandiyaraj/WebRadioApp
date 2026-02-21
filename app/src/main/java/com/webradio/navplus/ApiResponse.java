package com.webradio.navplus;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/** Wraps the radio.garden API responses */
public class ApiResponse {

    // For /ara/content/places
    public static class PlacesResponse {
        @SerializedName("data")
        public PlacesData data;

        public static class PlacesData {
            @SerializedName("list")
            public List<PlaceModel> list;
        }
    }

    // For /ara/content/page/{placeId}
    public static class PlacePageResponse {
        @SerializedName("data")
        public PageData data;

        public static class PageData {
            @SerializedName("content")
            public List<ContentSection> content;

            @SerializedName("title")
            public String title;

            @SerializedName("country")
            public CountryInfo country;

            public static class CountryInfo {
                @SerializedName("title")
                public String title;
            }
        }

        public static class ContentSection {
            @SerializedName("type")
            public String type;

            @SerializedName("itemsType")
            public String itemsType;

            @SerializedName("items")
            public List<StationItem> items;
        }

        public static class StationItem {
            @SerializedName("page")
            public StationPage page;

            @SerializedName("href")
            public String href;
        }

        public static class StationPage {
            @SerializedName("title")
            public String title;

            @SerializedName("url")
            public String url;

            @SerializedName("subtitle")
            public String subtitle;

            @SerializedName("place")
            public PlaceInfo place;

            @SerializedName("country")
            public PlaceInfo country;

            public static class PlaceInfo {
                @SerializedName("title")
                public String title;
            }
        }
    }
}
