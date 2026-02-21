# WebRadio Nav+ – Android App

## Overview
A native Android radio streaming app built in Java that lets users browse and play live internet radio stations from around the world using the [radio.garden](https://radio.garden) API. The UI is landscape-first and designed for in-car head units.

---

## Project Info

| Field | Value |
|---|---|
| Package | `com.webradio.navplus` |
| Language | Java |
| Min SDK | 26 (Android 8.0 Oreo) |
| Target / Compile SDK | 34 (Android 14) |
| Version | 1.0 (versionCode 1) |
| Build System | Gradle (Kotlin DSL) |
| View Binding | Enabled |

---

## Architecture

```
MainActivity (UI)
    │
    ├── ApiClient (Retrofit singleton)
    │       └── RadioGardenApi (Retrofit interface)
    │               ├── GET ara/content/places  → PlacesResponse → PlaceModel[]
    │               └── GET ara/content/page/{id} → PlacePageResponse → StationPage[]
    │
    ├── CountryListAdapter  (left panel – LinearLayoutManager)
    ├── StationAdapter      (main grid   – GridLayoutManager 3-col)
    │
    └── RadioService (Foreground Service – bound + started)
            └── ExoPlayer (media3) – streams channel.mp3
```

### App Flow

1. On launch → `loadPlaces()` fetches all places from `radio.garden/api/ara/content/places`.
2. Places are grouped by **country** into `countriesMap`; country names are sorted alphabetically into `countryList`.
3. `detectCountry()` calls `ipapi.co/json/` on a background thread to auto-select the user's country.
4. User taps a country → `loadStationsForCountry()` fetches up to **20 places in parallel**, aggregates all `channel`-type stations.
5. User taps a station → `playStation()` builds the stream URL `https://radio.garden/api/ara/content/listen/<channelId>/channel.mp3` and starts `RadioService`.
6. `RadioService` runs as a **foreground service** with a persistent media notification (Play/Pause + Stop actions).

---

## File Structure

```
WebRadioApp/
├── build.gradle.kts                  (root – no dependencies, just plugin classpath)
├── settings.gradle.kts
├── gradle.properties
├── local.properties
└── app/
    ├── build.gradle.kts              (app-level – all dependencies)
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/webradio/navplus/
        │   ├── MainActivity.java
        │   ├── RadioService.java
        │   ├── ApiClient.java
        │   ├── ApiResponse.java
        │   ├── RadioGardenApi.java
        │   ├── PlaceModel.java
        │   ├── CountryListAdapter.java
        │   └── StationAdapter.java
        └── res/
            ├── layout/
            │   ├── activity_main.xml
            │   ├── item_country.xml
            │   └── item_station.xml
            ├── drawable/
            │   ├── bg_logo_box.xml
            │   ├── bg_play_button.xml
            │   ├── bg_search.xml
            │   └── bg_station_logo.xml
            └── values/
                ├── colors.xml
                ├── strings.xml
                └── themes.xml
```

---

## Source Files

### `AndroidManifest.xml`
- **Permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- **Activity:** `MainActivity` — `exported=true`, `screenOrientation=landscape`, `windowSoftInputMode=adjustNothing`
- **Service:** `RadioService` — `exported=false`, `foregroundServiceType=mediaPlayback`
- **Application:** `usesCleartextTraffic=true` (needed for some stream URLs)

---

### `MainActivity.java`
Main and only Activity. Manages two panels and the bottom player bar.

**Fields:**
| Field | Type | Purpose |
|---|---|---|
| `allPlaces` | `List<PlaceModel>` | Raw list from API |
| `countriesMap` | `LinkedHashMap<String, List<PlaceModel>>` | Places grouped by country name |
| `countryList` | `List<String>` | Sorted unique country names |
| `filteredCountries` | `List<String>` | Country names matching search |
| `currentStations` | `List<StationPage>` | Stations for selected country |
| `selectedCountry` | `String` | Currently highlighted country |
| `currentStation` | `StationPage` | Currently playing station |
| `radioService` | `RadioService` | Bound service reference |
| `serviceBound` | `boolean` | Service bind state |

**Key Methods:**
| Method | Description |
|---|---|
| `setupCountryList()` | Configures `rvCountries` RecyclerView + search `TextWatcher` |
| `setupStationGrid()` | Configures `rvStations` GridLayoutManager (3 columns) |
| `setupPlayer()` | Wires play/pause button and volume SeekBar |
| `loadPlaces()` | Retrofit call → `getPlaces()` → `groupByCountry()` |
| `groupByCountry()` | Builds `countriesMap` + sorts `countryList` |
| `filterCountries(query)` | Filters `filteredCountries`, calls `notifyDataSetChanged()` |
| `loadStationsForCountry(country)` | Parallel Retrofit calls (up to 20 places), aggregates stations |
| `detectCountry()` | Background thread → `ipapi.co/json/` → auto-select country |
| `playStation(station)` | Updates UI, sends `ACTION_PLAY` intent to `RadioService` |
| `updatePlayButton()` | Syncs ▶/⏸ button text with service playing state |

**Service Binding:** `BIND_AUTO_CREATE` in `onStart()`, unbound in `onStop()`.

---

### `RadioService.java`
Foreground service for background audio playback using **ExoPlayer (media3)**.

**Actions (Intents):**
| Constant | Value |
|---|---|
| `ACTION_PLAY` | `com.webradio.navplus.PLAY` |
| `ACTION_PAUSE` | `com.webradio.navplus.PAUSE` |
| `ACTION_STOP` | `com.webradio.navplus.STOP` |

**Extras:** `EXTRA_STREAM_URL`, `EXTRA_STATION_TITLE`, `EXTRA_STATION_PLACE`

**Public API (via `RadioBinder`):**
| Method | Description |
|---|---|
| `playStream(url)` | Stops current, sets new MediaItem, prepares & plays, starts foreground |
| `togglePlayPause()` | Pauses if playing, resumes if paused |
| `setVolume(float)` | Sets ExoPlayer volume (0.0–1.0) |
| `isPlaying()` | Returns `player.isPlaying()` |
| `stop()` | Stops ExoPlayer |

**Notification:** `NotificationCompat` with `MediaStyle`. Channel ID: `radio_channel`, importance `LOW`, ongoing. Shows Play/Pause + Stop actions. Tapping opens `MainActivity`.

---

### `ApiClient.java`
Singleton Retrofit client.

- **Base URL:** `https://radio.garden/api/`
- **Headers injected:** `User-Agent: Mozilla/5.0 (Android; Mobile)`, `Referer: https://radio.garden/`
- **Timeouts:** 30s connect / 30s read
- **Converter:** `GsonConverterFactory`
- **Logging:** `HttpLoggingInterceptor.Level.BASIC`

---

### `RadioGardenApi.java`
Retrofit interface:

```java
@GET("ara/content/places")
Call<ApiResponse.PlacesResponse> getPlaces();

@GET("ara/content/page/{placeId}")
Call<ApiResponse.PlacePageResponse> getPlacePage(@Path("placeId") String placeId);
```

---

### `ApiResponse.java`
Nested POJO classes for Gson deserialization:

```
ApiResponse
├── PlacesResponse
│   └── PlacesData
│       └── list: List<PlaceModel>
└── PlacePageResponse
    ├── PageData
    │   ├── content: List<ContentSection>
    │   ├── title: String
    │   └── country: CountryInfo
    ├── ContentSection
    │   ├── type: String        ("list")
    │   ├── itemsType: String   ("channel")
    │   └── items: List<StationItem>
    ├── StationItem
    │   ├── page: StationPage
    │   └── href: String
    └── StationPage
        ├── title: String
        ├── url: String         ("/visit/…/<channelId>")
        ├── subtitle: String
        ├── place: PlaceInfo
        └── country: PlaceInfo
```

Stream URL construction:
```
https://radio.garden/api/ara/content/listen/<channelId>/channel.mp3
where channelId = url.substring(url.lastIndexOf('/') + 1)
```

---

### `PlaceModel.java`
Maps a single entry from the `/ara/content/places` list response.

| Field | Type | Notes |
|---|---|---|
| `id` | String | Place ID used in `/page/{id}` calls |
| `title` | String | City/place name |
| `country` | String | Country name (used for grouping) |
| `url` | String | Relative URL |
| `size` | int | Station count hint |
| `geo` | `List<Double>` | `[longitude, latitude]` |

---

### `CountryListAdapter.java`
`RecyclerView.Adapter` for the left country sidebar.

- Layout: `item_country.xml` → `TextView` with id `tv_country_name`
- Active country highlighted: text color `R.color.accent`, background `R.color.card_active`
- Inactive: text color `R.color.text_secondary`, transparent background
- `setActiveCountry(country)` → `notifyDataSetChanged()`

---

### `StationAdapter.java`
`RecyclerView.Adapter` for the 3-column station grid.

- Layout: `item_station.xml` → `CardView (card_station)`, `TextView (tv_station_name)`, `TextView (tv_station_city)`
- Active card: background `R.color.card_active`, name color `R.color.accent`
- Inactive card: background `R.color.card_bg`, name color `R.color.text_primary`
- Uses `notifyItemChanged(prev)` + `notifyItemChanged(activePosition)` for efficient updates
- `setActivePosition(int)` available for external control

---

## Dependencies (`app/build.gradle.kts`)

| Library | Version | Purpose |
|---|---|---|
| `androidx.appcompat:appcompat` | 1.6.1 | AppCompatActivity |
| `com.google.android.material:material` | 1.11.0 | Material UI components |
| `androidx.constraintlayout:constraintlayout` | 2.1.4 | Layouts |
| `androidx.recyclerview:recyclerview` | 1.3.2 | Country list + station grid |
| `androidx.cardview:cardview` | 1.0.0 | Station cards |
| `androidx.core:core` | 1.12.0 | Core KTX utilities |
| `androidx.media:media` | 1.7.0 | MediaStyle notification support |
| `androidx.media3:media3-exoplayer` | 1.2.1 | Audio streaming |
| `androidx.media3:media3-session` | 1.2.1 | Media session integration |
| `androidx.media3:media3-ui` | 1.2.1 | (available for future UI controls) |
| `com.squareup.retrofit2:retrofit` | 2.9.0 | HTTP client |
| `com.squareup.retrofit2:converter-gson` | 2.9.0 | JSON deserialization |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | Raw HTTP (used in `detectCountry()`) |
| `com.squareup.okhttp3:logging-interceptor` | 4.12.0 | Request logging |
| `com.github.bumptech.glide:glide` | 4.16.0 | Image loading (station logos) |

---

## Build & Run

```bash
# Debug APK
./gradlew assembleDebug

# Install directly to connected device
./gradlew installDebug

# Release APK (signing not configured)
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

---

## Known Notes / Gotchas

- `detectCountry()` runs on a raw `new Thread()`; if places haven't loaded yet when the country detection completes, the auto-select silently does nothing (no retry).
- `loadStationsForCountry()` fetches up to **20 places in parallel** using `synchronized (currentStations)` and a `pending[0]` counter to know when all requests are done.
- Stream URL format: `channel.mp3` endpoint (unofficial radio.garden API — may require `Referer` header spoofing, handled in `ApiClient`).
- `usesCleartextTraffic=true` is set globally; if tightening security is needed, consider a network security config.
- Glide is included as a dependency but not yet wired up in the adapters — station logo images currently fallback to text-only cards.
- `StationModel.java` file exists in the project but is not actively used (dead code from an earlier iteration).
