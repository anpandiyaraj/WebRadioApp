package com.webradio.navplus;

import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RadioService extends MediaSessionService {

    private MediaSession mediaSession;
    private ExoPlayer player;
    private static final String TAG = "RadioService";

    private class RadioCallback implements MediaSession.Callback {
        @Override
        public MediaSession.ConnectionResult onConnect(
                MediaSession session,
                MediaSession.ControllerInfo controller
        ) {
            // Add any connection logic here
            return MediaSession.Callback.super.onConnect(session, controller);
        }

        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(
                MediaSession mediaSession,
                MediaSession.ControllerInfo controller,
                List<MediaItem> mediaItems
        ) {
            // Your logic to handle adding media items
            return Futures.immediateFuture(mediaItems);
        }

        @Override
        public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
            MediaSession mediaSession,
            MediaSession.ControllerInfo controller
        ) {
            if (player.getCurrentMediaItem() != null) {
                List<MediaItem> mediaItems = Collections.singletonList(player.getCurrentMediaItem());
                player.prepare();
                player.play();
                return Futures.immediateFuture(
                    new MediaSession.MediaItemsWithStartPosition(mediaItems, player.getCurrentMediaItemIndex(), player.getCurrentPosition())
                );
            }
            // By default, do nothing and timings will be unset.
            return Futures.immediateFuture(
                new MediaSession.MediaItemsWithStartPosition(Collections.emptyList(), C.INDEX_UNSET, C.TIME_UNSET)
            );
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .addInterceptor(new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request newRequest = chain.request().newBuilder()
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.0.0 Mobile Safari/537.36")
                        .build();
                    return chain.proceed(newRequest);
                }
            })
            .build();

        DataSource.Factory dataSourceFactory = new OkHttpDataSource.Factory(okHttpClient);

        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(dataSourceFactory);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Player error: " + error.toString(), error);
            }
        });

        Intent sessionActivityIntent = new Intent(this, MainActivity.class);
        PendingIntent sessionActivityPendingIntent = PendingIntent.getActivity(
                this,
                0,
                sessionActivityIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        mediaSession = new MediaSession.Builder(this, player)
                .setCallback(new RadioCallback())
                .setSessionActivity(sessionActivityPendingIntent)
                .build();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!player.getPlayWhenReady()) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        mediaSession.release();
        player.release();
        super.onDestroy();
    }
}
