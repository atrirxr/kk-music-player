package com.shahadot.android_music_player;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.bumptech.glide.Glide;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.shahadot.android_music_player.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MediaController mediaController;
    private final Handler handler = new Handler();
    private final NowPlayingStore store = NowPlayingStore.getInstance();
    private long currentMiniSongId = -1;

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
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new MusicFragment())
                    .commit();
        }

        binding.navView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();
            if (id == R.id.nav_music) {
                fragment = new MusicFragment();
            } else if (id == R.id.nav_cloud) {
                fragment = new CloudFragment();
            } else if (id == R.id.nav_settings) {
                fragment = new SettingsFragment();
            }
            if (fragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit();
                return true;
            }
            return false;
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
            if (song.id != currentMiniSongId) {
                currentMiniSongId = song.id;
                binding.miniTitle.setText(song.title != null ? song.title : "No Title");
                binding.miniArtist.setText(song.artist != null ? song.artist : "No Artist");

                Uri albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), song.albumId);
                Glide.with(MainActivity.this)
                        .load(albumArtUri)
                        .circleCrop()
                        .placeholder(R.drawable.ic_music_note_24)
                        .error(R.drawable.ic_music_note_24)
                        .into(binding.miniAlbumArt);
            }
        });
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
