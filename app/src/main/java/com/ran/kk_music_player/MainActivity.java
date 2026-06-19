package com.ran.kk_music_player;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.ran.kk_music_player.databinding.ActivityMainBinding;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MediaController mediaController;
    private final Handler handler = new Handler();
    private final NowPlayingStore store = NowPlayingStore.getInstance();
    private long currentMiniSongId = -1;

    private Fragment musicFragment;
    private Fragment cloudFragment;
    private Fragment settingsFragment;
    private Fragment currentFragment;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaController == null || !store.isPlaying) {
                return;
            }
            long position = mediaController.getCurrentPosition();
            long duration = mediaController.getDuration();
            store.position = position;
            store.duration = duration;
            updateMiniPlayerUI(position, duration);
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        1001);
            }
        }

        if (savedInstanceState == null) {
            musicFragment = new MusicFragment();
            cloudFragment = new CloudFragment();
            settingsFragment = new SettingsFragment();
            currentFragment = musicFragment;

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, musicFragment, "music")
                    .add(R.id.fragment_container, cloudFragment, "cloud")
                    .hide(cloudFragment)
                    .add(R.id.fragment_container, settingsFragment, "settings")
                    .hide(settingsFragment)
                    .commit();
        } else {
            musicFragment = getSupportFragmentManager().findFragmentByTag("music");
            cloudFragment = getSupportFragmentManager().findFragmentByTag("cloud");
            settingsFragment = getSupportFragmentManager().findFragmentByTag("settings");
            currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        }

        binding.navView.setOnItemSelectedListener(item -> {
            Fragment target = null;
            int id = item.getItemId();
            if (id == R.id.nav_music) target = musicFragment;
            else if (id == R.id.nav_cloud) target = cloudFragment;
            else if (id == R.id.nav_settings) target = settingsFragment;

            if (target != null && target != currentFragment) {
                getSupportFragmentManager().beginTransaction()
                        .hide(currentFragment)
                        .show(target)
                        .commit();
                currentFragment = target;
            }
            return true;
        });

        binding.miniPlayer.setOnClickListener(v -> openPlayerActivity());
        connectToMediaSession();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Sync mini-player state when returning from PlayerActivity
        if (store.currentSong != null) {
            updateMiniPlayerSong(store.currentSong);
            showMiniPlayer();
            if (store.isPlaying && mediaController != null) {
                handler.post(updateRunnable);
            }
        } else {
            hideMiniPlayer();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted - notification channel will be created
            } else {
                // Permission denied - user may not see notifications
            }
        }
    }

    private void connectToMediaSession() {
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicService.class));
        ListenableFuture<MediaController> controllerFuture =
                new MediaController.Builder(this, sessionToken).buildAsync();

        Futures.addCallback(controllerFuture, new FutureCallback<MediaController>() {
            @Override
            public void onSuccess(MediaController controller) {
                mediaController = controller;

                if (store.currentSong != null) {
                    updateMiniPlayerSong(store.currentSong);
                    showMiniPlayer();
                    handler.post(updateRunnable);
                }

                controller.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        if (playbackState == Player.STATE_READY && store.currentSong != null) {
                            updateMiniPlayerSong(store.currentSong);
                            showMiniPlayer();
                            handler.post(updateRunnable);
                        } else if (playbackState == Player.STATE_ENDED) {
                            hideMiniPlayer();
                        }
                    }

                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        store.isPlaying = isPlaying;
                        if (isPlaying) {
                            showMiniPlayer();
                            handler.post(updateRunnable);
                        }
                    }

                    @Override
                    public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                        if (store.currentSong != null) {
                            updateMiniPlayerSong(store.currentSong);
                            showMiniPlayer();
                            handler.post(updateRunnable);
                        }
                    }
                });
            }

            @Override
            public void onFailure(Throwable t) {
                // MediaSession not available yet — will connect when playback starts
            }
        }, MoreExecutors.directExecutor());
    }

    private void showMiniPlayer() {
        runOnUiThread(() -> binding.miniPlayer.setVisibility(View.VISIBLE));
    }

    private void hideMiniPlayer() {
        runOnUiThread(() -> binding.miniPlayer.setVisibility(View.GONE));
    }

    private void updateMiniPlayerSong(Song song) {
        runOnUiThread(() -> {
            if (song.id == currentMiniSongId) return;
            currentMiniSongId = song.id;
            binding.miniTitle.setText(song.title != null ? song.title : "No Title");
            binding.miniArtist.setText(song.artist != null ? song.artist : "No Artist");
            loadMiniPlayerCover(song);
        });
    }

    private void loadMiniPlayerCover(Song song) {
        Object source;
        if (isCloudSong(song)) {
            File cacheFile = new File(getCacheDir(),
                    "album_covers/" + song.data.hashCode() + ".jpg");
            if (!cacheFile.exists()) {
                fallbackMiniPlayerCover();
                return;
            }
            source = Uri.fromFile(cacheFile);
        } else {
            source = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), song.albumId);
        }

        Glide.with(MainActivity.this)
                .asBitmap()
                .load(source)
                .circleCrop()
                .placeholder(R.drawable.ic_music_note_24)
                .error(R.drawable.ic_music_note_24)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource,
                                                @Nullable Transition<? super Bitmap> transition) {
                        binding.miniAlbumArt.setImageBitmap(resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        binding.miniAlbumArt.setImageDrawable(placeholder);
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        binding.miniAlbumArt.setImageDrawable(errorDrawable);
                    }
                });
    }

    private void fallbackMiniPlayerCover() {
        binding.miniAlbumArt.setImageResource(R.drawable.ic_music_note_24);
    }

    private boolean isCloudSong(Song song) {
        return song.albumId == 0
                && song.data != null
                && (song.data.startsWith("http://") || song.data.startsWith("https://"));
    }

    private void updateMiniPlayerUI(long position, long duration) {
        if (duration > 0) {
            binding.miniProgress.setProgress((int) ((position * 1000) / duration));
        }
        binding.miniElapsed.setText(formatTime((int) (position / 1000)));
    }

    private void openPlayerActivity() {
        if (store.currentSong != null) {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("open_only", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        }
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(updateRunnable);
        if (mediaController != null) {
            mediaController.release();
            mediaController = null;
        }
        super.onDestroy();
    }
}
