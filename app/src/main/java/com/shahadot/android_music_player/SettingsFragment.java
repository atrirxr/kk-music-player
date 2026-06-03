package com.shahadot.android_music_player;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private LinearLayout loginForm;
    private LinearLayout loggedInState;
    private EditText etEmail, etPassword;
    private TextView tvLoggedInEmail;
    private SeekBar volumeSeekBar;
    private TextView tvVolumePercent;
    private AudioManager audioManager;
    private SharedPreferences preferences;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        preferences = getActivity().getSharedPreferences("account", Context.MODE_PRIVATE);
        audioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);

        loginForm = view.findViewById(R.id.loginForm);
        loggedInState = view.findViewById(R.id.loggedInState);
        etEmail = view.findViewById(R.id.etEmail);
        etPassword = view.findViewById(R.id.etPassword);
        tvLoggedInEmail = view.findViewById(R.id.tvLoggedInEmail);
        volumeSeekBar = view.findViewById(R.id.volumeSeekBar);
        tvVolumePercent = view.findViewById(R.id.tvVolumePercent);

        updateLoginState();

        view.findViewById(R.id.btnLogin).setOnClickListener(v -> login());
        view.findViewById(R.id.btnRegister).setOnClickListener(v -> register());
        view.findViewById(R.id.btnLogout).setOnClickListener(v -> logout());

        setupVolumeControl();

        return view;
    }

    private void updateLoginState() {
        String email = preferences.getString("email", null);
        if (email != null) {
            loginForm.setVisibility(View.GONE);
            loggedInState.setVisibility(View.VISIBLE);
            tvLoggedInEmail.setText(email);
        } else {
            loginForm.setVisibility(View.VISIBLE);
            loggedInState.setVisibility(View.GONE);
        }
    }

    private void login() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(getActivity(), "请填写邮箱和密码", Toast.LENGTH_SHORT).show();
            return;
        }

        String savedPassword = preferences.getString("password_" + email, null);
        if (savedPassword == null) {
            Toast.makeText(getActivity(), "该邮箱未注册", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!savedPassword.equals(password)) {
            Toast.makeText(getActivity(), "密码错误", Toast.LENGTH_SHORT).show();
            return;
        }

        preferences.edit().putString("email", email).apply();
        updateLoginState();
        etEmail.setText("");
        etPassword.setText("");
        Toast.makeText(getActivity(), "登录成功", Toast.LENGTH_SHORT).show();
    }

    private void register() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(getActivity(), "请填写邮箱和密码", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(getActivity(), "密码至少6位", Toast.LENGTH_SHORT).show();
            return;
        }

        if (preferences.contains("password_" + email)) {
            Toast.makeText(getActivity(), "该邮箱已注册", Toast.LENGTH_SHORT).show();
            return;
        }

        preferences.edit()
                .putString("password_" + email, password)
                .putString("email", email)
                .apply();
        updateLoginState();
        etEmail.setText("");
        etPassword.setText("");
        Toast.makeText(getActivity(), "注册成功", Toast.LENGTH_SHORT).show();
    }

    private void logout() {
        preferences.edit().remove("email").apply();
        updateLoginState();
        Toast.makeText(getActivity(), "已退出登录", Toast.LENGTH_SHORT).show();
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
}
