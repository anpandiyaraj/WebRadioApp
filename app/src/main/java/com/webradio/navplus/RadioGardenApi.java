package com.webradio.navplus;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface RadioGardenApi {

    /** Get all places (countries/cities) */
    @GET("ara/content/places")
    Call<ApiResponse.PlacesResponse> getPlaces();

    /** Get stations for a specific place */
    @GET("ara/content/page/{placeId}")
    Call<ApiResponse.PlacePageResponse> getPlacePage(@Path("placeId") String placeId);
}
