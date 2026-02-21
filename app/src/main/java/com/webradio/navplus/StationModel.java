package com.webradio.navplus;

import com.google.gson.annotations.SerializedName;

public class StationModel {
    @SerializedName("id")
    public String id;

    @SerializedName("title")
    public String title;

    @SerializedName("url")
    public String url;

    // Resolved from the 'page' wrapper
    public String placeTitle;
    public String countryTitle;

    public String getChannelId() {
        if (url == null) return null;
        return url.substring(url.lastIndexOf('/') + 1);
    }

    public String getStreamUrl() {
        String channelId = getChannelId();
        if (channelId == null) return null;
        return "https://radio.garden/api/ara/content/listen/" + channelId + "/channel.mp3";
    }
}
