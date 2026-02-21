package com.webradio.navplus;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class PlaceModel {
    @SerializedName("id")
    public String id;

    @SerializedName("title")
    public String title;

    @SerializedName("country")
    public String country;

    @SerializedName("url")
    public String url;

    @SerializedName("size")
    public int size;

    @SerializedName("geo")
    public List<Double> geo;
}
