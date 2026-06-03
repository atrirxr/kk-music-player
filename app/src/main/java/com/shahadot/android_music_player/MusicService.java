package com.shahadot.android_music_player;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class MusicService extends MediaSessionService {

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "music_channel";

    private ExoPlayer player;
    private MediaSession mediaSession;

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        NowPlayingStore store = NowPlayingStore.getInstance();

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    request = request.newBuilder()
                            .header("User-Agent", "AndroidMusicPlayer/1.0")
                            .build();
                    Map<String, String> headers = store.requestHeaders;
                    if (headers != null && !headers.isEmpty()) {
                        Request.Builder builder = request.newBuilder();
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            builder.header(entry.getKey(), entry.getValue());
                        }
                        request = builder.build();
                    }
                    return chain.proceed(request);
                })
                .build();


        OkHttpDataSource.Factory httpFactory = new OkHttpDataSource.Factory(okHttpClient);
        FileDataSource.Factory fileFactory = new FileDataSource.Factory();

        DataSource.Factory dataSourceFactory = () -> new DataSource() {
            private DataSource delegate;

            @Override
            public void addTransferListener(TransferListener transferListener) {}

            @Override
            public long open(DataSpec dataSpec) throws IOException {
                Uri uri = dataSpec.uri;
                String scheme = uri.getScheme();
                delegate = ("http".equals(scheme) || "https".equals(scheme))
                        ? httpFactory.createDataSource()
                        : fileFactory.createDataSource();
                return delegate.open(dataSpec);
            }

            @Override
            public int read(byte[] buf, int off, int len) throws IOException {
                return delegate.read(buf, off, len);
            }

            @Override
            public Uri getUri() {
                return delegate != null ? delegate.getUri() : null;
            }

            @Override
            public Map<String, List<String>> getResponseHeaders() {
                return delegate != null ? delegate.getResponseHeaders() : Collections.emptyMap();
            }

            @Override
            public void close() throws IOException {
                if (delegate != null) {
                    delegate.close();
                    delegate = null;
                }
            }
        };

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSourceFactory))
                .build();

        mediaSession = new MediaSession.Builder(this, player)
                .setSessionActivity(createSessionActivityPendingIntent())
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Music Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel.setShowBadge(false);
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private PendingIntent createSessionActivityPendingIntent() {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onUpdateNotification(@NonNull MediaSession session, boolean startInForeground) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note_24)
                .setContentTitle("Playing Music")
                .setContentIntent(createSessionActivityPendingIntent())
                .setStyle(new androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .build();

        if (startInForeground) {
            startForeground(NOTIFICATION_ID, notification);
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID, notification);
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaSession.release();
        player.release();
    }
}
