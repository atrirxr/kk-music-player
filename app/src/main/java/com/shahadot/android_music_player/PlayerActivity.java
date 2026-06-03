package com.shahadot.android_music_player;

import static com.bumptech.glide.request.RequestOptions.bitmapTransform;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.bumptech.glide.Glide;
import com.frolo.waveformseekbar.WaveformSeekBar;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.shahadot.android_music_player.databinding.ActivityPlayerBinding;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import jp.wasabeef.glide.transformations.BlurTransformation;

public class PlayerActivity extends AppCompatActivity {

    private ActivityPlayerBinding binding;
    private MediaController mediaController;
    private final Handler handler = new Handler();

    private List<Song> songList = new ArrayList<>();
    private int currentIndex = 0;

    private final NowPlayingStore store = NowPlayingStore.getInstance();

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaController != null && mediaController.isPlaying()) {
                long currentPosition = mediaController.getCurrentPosition();
                long duration = mediaController.getDuration();
                if (duration > 0) {
                    float progress = ((float) currentPosition / duration);
                    binding.waveformSeekBar.setProgressInPercentage(progress);
                    binding.textElapsed.setText(formatTime((int) (currentPosition / 1000)));
                    binding.textDuration.setText(formatTime((int) (duration / 1000)));
                }
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        boolean openOnly = getIntent().getBooleanExtra("open_only", false);

        if (openOnly && store.currentSong != null) {
            // Opened from mini-player — restore state and don't restart playback
            songList = store.songList != null ? store.songList : new ArrayList<>();
            currentIndex = store.currentIndex;
            binding.waveformSeekBar.setWaveform(createWaveForm(), true);
            connectToSession(false);
        } else {
            songList = getIntent().getParcelableArrayListExtra("songList");
            currentIndex = getIntent().getIntExtra("position", 0);

            if (songList == null || songList.isEmpty()) {
                Toast.makeText(this, "No songs found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            store.songList = songList;
            store.currentIndex = currentIndex;

            binding.waveformSeekBar.setWaveform(createWaveForm(), true);
            connectToSession(true);
        }

        setupControls();

        binding.backBtn.setOnClickListener(v -> finish());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        boolean openOnly = intent.getBooleanExtra("open_only", false);
        if (openOnly && store.currentSong != null) {
            // Already connected, just update UI
            if (store.currentSong != null) {
                updateUI(store.currentSong);
                if (mediaController != null && mediaController.isPlaying()) {
                    handler.postDelayed(updateRunnable, 0);
                }
            }
        }
    }

    private void connectToSession(boolean playImmediately) {
        Song song = songList.get(currentIndex);
        store.currentSong = song;

        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicService.class));
        MediaController.Builder builder = new MediaController.Builder(this, sessionToken);
        ListenableFuture<MediaController> controllerFuture = builder.buildAsync();

        Futures.addCallback(controllerFuture, new FutureCallback<MediaController>() {
            @Override
            public void onSuccess(MediaController controller) {
                if (mediaController != null) {
                    mediaController.release();
                }

                mediaController = controller;

                mediaController.addListener(new Player.Listener() {
                    @Override
                    public void onPlayerError(@NonNull PlaybackException error) {
                        Toast.makeText(PlayerActivity.this, "Playback Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        updatePlayPauseIcon();
                        if (playbackState == Player.STATE_READY) {
                            handler.postDelayed(updateRunnable, 0);
                        } else if (playbackState == Player.STATE_ENDED) {
                            playNext();
                        }
                    }
                });

                if (playImmediately) {
                    mediaController.setMediaItem(MediaItem.fromUri(song.data));
                    mediaController.prepare();
                    mediaController.play();
                } else {
                    // Sync position from already-playing session
                    if (mediaController.isPlaying() || mediaController.getPlaybackState() == Player.STATE_READY) {
                        long pos = mediaController.getCurrentPosition();
                        long dur = mediaController.getDuration();
                        if (dur > 0) {
                            binding.waveformSeekBar.setProgressInPercentage((float) pos / dur);
                            binding.textElapsed.setText(formatTime((int) (pos / 1000)));
                            binding.textDuration.setText(formatTime((int) (dur / 1000)));
                        }
                        handler.postDelayed(updateRunnable, 0);
                    }
                }

                updatePlayPauseIcon();
                updateUI(song);
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Toast.makeText(PlayerActivity.this, "Failed to connect to media session: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, MoreExecutors.directExecutor());
    }

    private void setupControls() {
        binding.buttonPlayPause.setOnClickListener(v -> togglePlayPause());
        binding.buttonNext.setOnClickListener(v -> playNext());
        binding.buttonPrev.setOnClickListener(v -> playPrev());

        binding.waveformSeekBar.setCallback(new WaveformSeekBar.Callback() {
            @Override
            public void onProgressChanged(WaveformSeekBar seekBar, float percent, boolean fromUser) {
                if (fromUser && mediaController != null) {
                    long duration = mediaController.getDuration();
                    long seekPosition = (long) (percent * duration);
                    mediaController.seekTo(seekPosition);
                    binding.textElapsed.setText(formatTime((int) (seekPosition / 1000)));
                }
            }

            @Override
            public void onStartTrackingTouch(WaveformSeekBar seekBar) {
                handler.removeCallbacks(updateRunnable);
            }

            @Override
            public void onStopTrackingTouch(WaveformSeekBar seekBar) {
                handler.postDelayed(updateRunnable, 0);
            }
        });
    }

    private void togglePlayPause() {
        if (mediaController != null) {
            if (mediaController.isPlaying()) {
                mediaController.pause();
                handler.removeCallbacks(updateRunnable);
            } else {
                mediaController.play();
                handler.postDelayed(updateRunnable, 0);
            }
            updatePlayPauseIcon();
        }
    }

    private void playSong(int index, boolean playImmediately) {
        Song song = songList.get(index);
        store.currentSong = song;
        store.currentIndex = index;

        if (mediaController != null) {
            if (playImmediately) {
                mediaController.setMediaItem(MediaItem.fromUri(song.data));
                mediaController.prepare();
                mediaController.play();
            }
            updateUI(song);
        } else {
            connectToSession(playImmediately);
        }
    }

    private void updateUI(Song song) {
        binding.textTitle.setText(song.title != null ? song.title : "No Title");
        binding.textArtist.setText(song.artist != null ? song.artist : "No Artist");
        setTitle(song.title);

        Uri albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId);

        if (hasAlbumArt(albumArtUri)) {
            Glide.with(this)
                    .asBitmap()
                    .load(albumArtUri)
                    .circleCrop()
                    .placeholder(R.drawable.ic_music_note_24)
                    .error(R.drawable.ic_music_note_24)
                    .into(binding.imageAlbumArtPlayer);

            Glide.with(this)
                    .asBitmap()
                    .load(albumArtUri)
                    .apply(bitmapTransform(new BlurTransformation(25, 3)))
                    .placeholder(R.drawable.ic_music_note_24)
                    .error(R.drawable.ic_music_note_24)
                    .into(binding.bgAlbumArt);
        } else {
            binding.imageAlbumArtPlayer.setImageResource(R.drawable.ic_music_note_24);
            binding.bgAlbumArt.setImageResource(R.drawable.ic_music_note_24);
        }
    }

    private boolean hasAlbumArt(Uri albumArtUri) {
        try (InputStream inputStream = getContentResolver().openInputStream(albumArtUri)) {
            return inputStream != null;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressLint("DefaultLocale")
    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private static final Random random = new Random();

    private int[] createWaveForm() {
        int[] values = new int[50];
        for (int i = 0; i < values.length; i++) {
            values[i] = 5 + random.nextInt(50);
        }
        return values;
    }

    private void updatePlayPauseIcon() {
        store.isPlaying = mediaController != null && mediaController.isPlaying();
        binding.buttonPlayPause.setImageResource(
                store.isPlaying ? R.drawable.ic_pause_24 : R.drawable.ic_play_arrow_24
        );
    }

    private void playNext() {
        currentIndex = (currentIndex + 1) % songList.size();
        store.currentIndex = currentIndex;
        playSong(currentIndex, true);
    }

    private void playPrev() {
        currentIndex = (currentIndex - 1 + songList.size()) % songList.size();
        store.currentIndex = currentIndex;
        playSong(currentIndex, true);
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
