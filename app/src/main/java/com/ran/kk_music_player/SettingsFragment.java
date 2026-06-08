package com.ran.kk_music_player;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private SeekBar volumeSeekBar;
    private TextView tvVolumePercent;
    private AudioManager audioManager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        audioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);

        volumeSeekBar = view.findViewById(R.id.volumeSeekBar);
        tvVolumePercent = view.findViewById(R.id.tvVolumePercent);

        setupVolumeControl();

        view.findViewById(R.id.aboutSection).setOnClickListener(v -> showAboutDialog());

        return view;
    }

    private void setupVolumeControl() {
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        volumeSeekBar.setMax(maxVolume);
        volumeSeekBar.setProgress(currentVolume);
        tvVolumePercent.setText(currentVolume + "/" + maxVolume);

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                }
                tvVolumePercent.setText(progress + "/" + maxVolume);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void showAboutDialog() {
        ImageView imageView = new ImageView(getActivity());
        imageView.setImageResource(R.drawable.about);
        imageView.setAdjustViewBounds(true);
        imageView.setMaxWidth(600);
        imageView.setPadding(0, 24, 0, 0);

        new AlertDialog.Builder(getActivity())
                .setTitle("关于")
                .setView(imageView)
                .setPositiveButton("确定", null)
                .show();
    }
}
