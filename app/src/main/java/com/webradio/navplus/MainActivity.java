package com.webradio.navplus;

import android.Manifest;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.webradio.navplus.databinding.ActivityMainBinding;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WebRadioMain";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private ActivityMainBinding binding;

    private ListenableFuture<MediaController> controllerFuture;
    private MediaController mediaController;

    private final List<PlaceModel> allPlaces = new ArrayList<>();
    private final Map<String, List<PlaceModel>> countriesMap = new LinkedHashMap<>();
    private final List<String> countryList = new ArrayList<>();
    private final List<String> filteredCountries = new ArrayList<>();

    private CountryListAdapter countryAdapter;
    private StationAdapter stationAdapter;

    private String selectedCountry = null;
    private final List<StationListItem> fullStationList = new ArrayList<>();
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setupCountryList();
        setupStationGrid();
        setupPlayer();
        loadPlaces();

        binding.etSearchStation.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                filterStations(s.toString());
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, RadioService.class));
        controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        controllerFuture.addListener(this::onMediaControllerReady, MoreExecutors.directExecutor());
    }

    private void onMediaControllerReady() {
        try {
            mediaController = controllerFuture.get();
            if (mediaController != null) {
                updateUiForPlayerState(mediaController.getPlayWhenReady(), mediaController.getCurrentMediaItem());
                mediaController.addListener(new Player.Listener() {
                    @Override
                    public void onEvents(Player player, Player.Events events) {
                        if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                            updateUiForPlayerState(player.getPlayWhenReady(), player.getCurrentMediaItem());
                        }
                    }
                });
            }
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, "Error getting media controller", e);
        }
    }

    private void setupCountryList() {
        countryAdapter = new CountryListAdapter(filteredCountries, country -> {
            selectedCountry = country;
            countryAdapter.setActiveCountry(country);
            loadStationsForCountry(country);
        });
        binding.rvCountries.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCountries.setAdapter(countryAdapter);
    }

    private void setupStationGrid() {
        stationAdapter = new StationAdapter(this::playStation);
        GridLayoutManager layoutManager = new GridLayoutManager(this, 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return stationAdapter.getItemViewType(position) == 0 ? 3 : 1;
            }
        });
        binding.rvStations.setLayoutManager(layoutManager);
        binding.rvStations.setAdapter(stationAdapter);
    }

    private void setupPlayer() {
        binding.btnPlayPause.setOnClickListener(v -> {
            if (mediaController == null) return;
            if (mediaController.isPlaying()) {
                mediaController.pause();
            } else {
                mediaController.play();
            }
        });

        binding.seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mediaController != null) {
                    mediaController.setVolume(progress / 100f);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        binding.seekVolume.setProgress(70);
    }

    private void loadPlaces() {
        binding.tvStatus.setText("Loading countries...");
        binding.progressCountries.setVisibility(View.VISIBLE);

        ApiClient.get().getPlaces().enqueue(new Callback<ApiResponse.PlacesResponse>() {
            @Override
            public void onResponse(Call<ApiResponse.PlacesResponse> call, retrofit2.Response<ApiResponse.PlacesResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<PlaceModel> places = response.body().data.list;
                    allPlaces.addAll(places);
                    groupByCountry(places);
                    runOnUiThread(() -> {
                        binding.progressCountries.setVisibility(View.GONE);
                        requestLocationPermission();
                    });
                }
            }
            @Override
            public void onFailure(Call<ApiResponse.PlacesResponse> call, Throwable t) {
                Log.e(TAG, "Failed to load places", t);
                runOnUiThread(() -> {
                    binding.progressCountries.setVisibility(View.GONE);
                    binding.tvStatus.setText("Failed to load. Check internet.");
                });
            }
        });
    }

    private void groupByCountry(List<PlaceModel> places) {
        countriesMap.clear();
        for (PlaceModel place : places) {
            countriesMap.computeIfAbsent(place.country, k -> new ArrayList<>()).add(place);
        }
        countryList.clear();
        countryList.addAll(new ArrayList<>(countriesMap.keySet()));
        countryList.sort(String::compareTo);
    }

    private void filterCountries(String query) {
        filteredCountries.clear();
        if (query.isEmpty()) {
            filteredCountries.addAll(countryList);
        } else {
            for (String c : countryList) {
                if (c.toLowerCase().contains(query.toLowerCase())) {
                    filteredCountries.add(c);
                }
            }
        }
        countryAdapter.notifyDataSetChanged();
    }

    private void loadStationsForCountry(String country) {
        stationAdapter.submitList(new ArrayList<>());
        fullStationList.clear();
        binding.tvStatus.setText(country);
        binding.progressStations.setVisibility(View.VISIBLE);

        List<PlaceModel> places = countriesMap.get(country);
        if (places == null || places.isEmpty()) {
            binding.progressStations.setVisibility(View.GONE);
            return;
        }

        List<ApiResponse.PlacePageResponse.StationPage> stations = new ArrayList<>();
        int[] pending = {Math.min(places.size(), 20)};
        final int total = pending[0];

        for (int i = 0; i < total; i++) {
            PlaceModel place = places.get(i);
            ApiClient.get().getPlacePage(place.id).enqueue(new Callback<ApiResponse.PlacePageResponse>() {
                @Override
                public void onResponse(Call<ApiResponse.PlacePageResponse> call, retrofit2.Response<ApiResponse.PlacePageResponse> response) {
                    synchronized (stations) {
                        if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                            List<ApiResponse.PlacePageResponse.ContentSection> sections = response.body().data.content;
                            if (sections != null) {
                                for (ApiResponse.PlacePageResponse.ContentSection section : sections) {
                                    if ("list".equals(section.type) && "channel".equals(section.itemsType) && section.items != null) {
                                        for (ApiResponse.PlacePageResponse.StationItem item : section.items) {
                                            if (item.page != null) {
                                                stations.add(item.page);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        pending[0]--;
                        if (pending[0] <= 0) {
                            runOnUiThread(() -> {
                                binding.progressStations.setVisibility(View.GONE);
                                Map<String, List<ApiResponse.PlacePageResponse.StationPage>> cityMap = new LinkedHashMap<>();
                                for (ApiResponse.PlacePageResponse.StationPage station : stations) {
                                    String city = station.place != null ? station.place.title : "Unknown";
                                    List<ApiResponse.PlacePageResponse.StationPage> cityStations = cityMap.computeIfAbsent(city, k -> new ArrayList<>());
                                    boolean exists = false;
                                    for(ApiResponse.PlacePageResponse.StationPage s : cityStations) {
                                        if (s.title.equals(station.title)) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) {
                                        cityStations.add(station);
                                    }
                                }

                                fullStationList.clear();
                                for (Map.Entry<String, List<ApiResponse.PlacePageResponse.StationPage>> entry : cityMap.entrySet()) {
                                    fullStationList.add(new StationListItem.HeaderItem(entry.getKey()));
                                    for(ApiResponse.PlacePageResponse.StationPage station : entry.getValue()){
                                        fullStationList.add(new StationListItem.StationItem(station));
                                    }
                                }
                                filterStations("");
                            });
                        }
                    }
                }
                @Override
                public void onFailure(Call<ApiResponse.PlacePageResponse> call, Throwable t) {
                    synchronized (stations) {
                        pending[0]--;
                        if (pending[0] <= 0) {
                            runOnUiThread(() -> binding.progressStations.setVisibility(View.GONE));
                        }
                    }
                }
            });
        }
    }

    private void filterStations(String query) {
        List<StationListItem> filteredList = new ArrayList<>();
        Map<String, List<StationListItem>> cityStationsMap = new LinkedHashMap<>();
        String lowerCaseQuery = query.toLowerCase();

        for (StationListItem item : fullStationList) {
            if (item instanceof StationListItem.StationItem) {
                StationListItem.StationItem stationItem = (StationListItem.StationItem) item;
                ApiResponse.PlacePageResponse.StationPage station = stationItem.getStation();

                String stationName = station.title != null ? station.title : "";
                String cityName = station.place != null ? station.place.title : "";

                if (query.isEmpty() ||
                    stationName.toLowerCase().contains(lowerCaseQuery) ||
                    cityName.toLowerCase().contains(lowerCaseQuery)) {

                    String cityGroup = station.place != null ? station.place.title : "Unknown";
                    cityStationsMap.computeIfAbsent(cityGroup, k -> new ArrayList<>()).add(stationItem);
                }
            }
        }

        for (Map.Entry<String, List<StationListItem>> entry : cityStationsMap.entrySet()) {
            filteredList.add(new StationListItem.HeaderItem(entry.getKey()));
            filteredList.addAll(entry.getValue());
        }

        stationAdapter.submitList(filteredList);
    }

    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            detectCountry();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                detectCountry();
            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show();
                setDefaultCountryToIndia();
            }
        }
    }

    private void detectCountry() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return; // Permissions not granted
        }

        Toast.makeText(this, "Detecting country using location...", Toast.LENGTH_SHORT).show();
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                getCountryFromLocation(location);
            } else {
                Toast.makeText(this, "Location not available, falling back to IP detection.", Toast.LENGTH_SHORT).show();
                // Fallback to IP-based detection if location is null
                detectCountryWithIp();
            }
        });
    }

    private void getCountryFromLocation(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                String countryName = addresses.get(0).getCountryName();
                if (countryName != null && !countryName.isEmpty()) {
                    filterCountries(countryName);
                    if (!filteredCountries.isEmpty()) {
                        String bestMatch = filteredCountries.get(0);
                        Toast.makeText(this, "Detected country: " + bestMatch, Toast.LENGTH_SHORT).show();
                        selectedCountry = bestMatch;
                        countryAdapter.setActiveCountry(bestMatch);
                        loadStationsForCountry(bestMatch);
                    } else {
                        setDefaultCountryToIndia();
                    }
                } else {
                    setDefaultCountryToIndia();
                }
            } else {
                setDefaultCountryToIndia();
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoder failed", e);
            setDefaultCountryToIndia();
        }
    }

    private void detectCountryWithIp() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request newRequest = chain.request().newBuilder()
                            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Mobile Safari/537.36")
                            .build();
                        return chain.proceed(newRequest);
                    })
                    .build();
                Request request = new Request.Builder().url("https://ipapi.co/json/").build();
                okhttp3.Response response = client.newCall(request).execute();
                if (response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    String countryName = json.optString("country_name", null);
                    if (countryName != null && !countryName.isEmpty()) {
                        runOnUiThread(() -> {
                            filterCountries(countryName);
                            if (!filteredCountries.isEmpty()) {
                                String bestMatch = filteredCountries.get(0);
                                Toast.makeText(this, "Detected country via IP: " + bestMatch, Toast.LENGTH_SHORT).show();
                                selectedCountry = bestMatch;
                                countryAdapter.setActiveCountry(bestMatch);
                                loadStationsForCountry(bestMatch);
                            } else {
                                setDefaultCountryToIndia();
                            }
                        });
                    } else {
                        runOnUiThread(this::setDefaultCountryToIndia);
                    }
                } else {
                    runOnUiThread(this::setDefaultCountryToIndia);
                }
            } catch (Exception e) {
                Log.e(TAG, "Country detect failed", e);
                runOnUiThread(this::setDefaultCountryToIndia);
            }
        }).start();
    }

    private void setDefaultCountryToIndia() {
        Toast.makeText(this, "Could not automatically detect your country, or no stations available. Defaulting to India.", Toast.LENGTH_LONG).show();
        filterCountries("India");
        if (countryList.contains("India")) {
            selectedCountry = "India";
            countryAdapter.setActiveCountry("India");
            loadStationsForCountry("India");
        } else {
            filterCountries("");
            binding.tvStatus.setText("Select a Country");
        }
    }

    private void playStation(ApiResponse.PlacePageResponse.StationPage station) {
        if (mediaController == null || station.url == null) return;
        String channelId = station.url.substring(station.url.lastIndexOf('/') + 1);
        String streamUrl = "https://radio.garden/api/ara/content/listen/" + channelId + "/channel.mp3";
        Log.d(TAG, "Playing stream: " + streamUrl);

        Bundle extras = new Bundle();
        extras.putString("place", station.place != null ? station.place.title : "");
        extras.putString("country", station.country != null ? station.country.title : "");

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(station.title)
                        .setExtras(extras)
                        .build())
                .build();

        mediaController.setMediaItem(mediaItem);
        mediaController.prepare();
        mediaController.play();
    }

    private void updateUiForPlayerState(boolean isPlaying, @Nullable MediaItem mediaItem) {
        binding.btnPlayPause.setText(isPlaying ? "⏸" : "▶");

        if (mediaItem != null && mediaItem.mediaMetadata != null && mediaItem.mediaMetadata.title != null) {
            String title = mediaItem.mediaMetadata.title.toString();
            String place = "";
            String country = "";
            if (mediaItem.mediaMetadata.extras != null) {
                place = mediaItem.mediaMetadata.extras.getString("place", "");
                country = mediaItem.mediaMetadata.extras.getString("country", "");
            }

            binding.tvNowPlaying.setText(title);
            binding.tvNowStation.setText(place + ", " + country);
            binding.btnPlayPause.setEnabled(true);
        } else {
            binding.tvNowPlaying.setText("No station selected");
            binding.tvNowStation.setText("-");
            binding.btnPlayPause.setEnabled(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaController != null) {
            MediaController.releaseFuture(controllerFuture);
        }
    }
}
