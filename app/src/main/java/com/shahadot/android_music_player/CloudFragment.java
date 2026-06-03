package com.shahadot.android_music_player;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shahadot.android_music_player.WebDavClient.CloudFile;
import com.shahadot.android_music_player.databinding.FragmentCloudBinding;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class CloudFragment extends Fragment implements CloudSongAdapter.OnItemClickListener {

    private FragmentCloudBinding binding;
    private WebDavClient webDavClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isDestroyed = new AtomicBoolean(false);

    private CloudSongAdapter adapter;
    private List<CloudFile> currentFiles = new ArrayList<>();
    private String currentPath = "/";

    private static final String PREFS_NAME = "cloud_prefs";
    private static final String KEY_URL = "server_url";
    private static final String KEY_USER = "username";
    private static final String KEY_PASS = "password";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentCloudBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        loadSavedCredentials();
        setupClickListeners();
    }

    private void setupRecyclerView() {
        binding.recyclerViewCloud.setLayoutManager(new LinearLayoutManager(getActivity()));
        adapter = new CloudSongAdapter(currentFiles, this);
        binding.recyclerViewCloud.setAdapter(adapter);
    }

    private void loadSavedCredentials() {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String url = prefs.getString(KEY_URL, "");
        String user = prefs.getString(KEY_USER, "");
        String pass = prefs.getString(KEY_PASS, "");

        if (!url.isEmpty()) {
            binding.etServerUrl.setText(url);
            binding.etUsername.setText(user);
            binding.etPassword.setText(pass);
            autoConnect(url, user, pass);
        }
    }

    private String getConnectionError(int httpCode) {
        switch (httpCode) {
            case 401: return "认证失败，请检查用户名和密码（使用 Alist 登录账号）";
            case 403: return "服务器拒绝访问，请检查路径权限";
            case 404: return "WebDAV 地址不正确，注意 Alist 需要使用 /dav 路径";
            case 500: return "服务器内部错误，请检查 Alist 服务状态";
            default:  return "连接失败 (HTTP " + httpCode + ")，请检查地址";
        }
    }

    private void autoConnect(String url, String user, String pass) {
        showLoading(true);
        showLoginError(null);
        executor.execute(() -> {
            if (isDestroyed.get()) return;
            WebDavClient client = new WebDavClient(url, user, pass);
            try {
                int httpCode = client.testConnection();
                mainHandler.post(() -> {
                    if (isDestroyed.get()) return;
                    if (httpCode == 207 || httpCode == 200 || httpCode == 301 || httpCode == 302) {
                        webDavClient = client;
                        saveCredentials(url, user, pass);
                        showBrowser();
                        loadFileList("/");
                    } else {
                        showLoading(false);
                        showLoginError(getConnectionError(httpCode));
                    }
                });
            } catch (IOException e) {
                mainHandler.post(() -> {
                    if (isDestroyed.get()) return;
                    showLoading(false);
                    showLoginError("无法连接到服务器:\n" + e.getClass().getSimpleName() + ": " + e.getLocalizedMessage()
                            + "\n\n连接地址: " + client.getConnectUrl());
                });
            }
        });
    }

    private void saveCredentials(String url, String user, String pass) {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_URL, url)
                .putString(KEY_USER, user)
                .putString(KEY_PASS, pass)
                .apply();
    }

    private void setupClickListeners() {
        binding.btnConnect.setOnClickListener(v -> {
            String url = binding.etServerUrl.getText().toString().trim();
            String user = binding.etUsername.getText().toString().trim();
            String pass = binding.etPassword.getText().toString().trim();

            if (url.isEmpty()) {
                showLoginError("请输入服务器地址");
                return;
            }

            showLoading(true);
            showLoginError(null);

            executor.execute(() -> {
                if (isDestroyed.get()) return;
                WebDavClient client = new WebDavClient(url, user, pass);
                try {
                    int httpCode = client.testConnection();
                    mainHandler.post(() -> {
                        if (isDestroyed.get()) return;
                        if (httpCode == 207 || httpCode == 200 || httpCode == 301 || httpCode == 302) {
                            webDavClient = client;
                            saveCredentials(url, user, pass);
                            showBrowser();
                            loadFileList("/");
                        } else {
                            showLoading(false);
                            showLoginError(getConnectionError(httpCode));
                        }
                    });
                } catch (IOException e) {
                    mainHandler.post(() -> {
                        if (isDestroyed.get()) return;
                        showLoading(false);
                        showLoginError("无法连接到服务器:\n" + e.getClass().getSimpleName() + ": " + e.getLocalizedMessage()
                                + "\n\n连接地址: " + client.getConnectUrl());
                    });
                }
            });
        });

        binding.btnDisconnect.setOnClickListener(v -> disconnect());

        binding.btnBack.setOnClickListener(v -> navigateUp());
    }

    private void disconnect() {
        webDavClient = null;
        currentPath = "/";
        currentFiles.clear();
        adapter.notifyDataSetChanged();
        showLogin();
    }

    private void showLogin() {
        binding.loginPanel.setVisibility(View.VISIBLE);
        binding.browserPanel.setVisibility(View.GONE);
        binding.loadingOverlay.setVisibility(View.GONE);
    }

    private void showBrowser() {
        binding.loginPanel.setVisibility(View.GONE);
        binding.browserPanel.setVisibility(View.VISIBLE);
        binding.loadingOverlay.setVisibility(View.GONE);
    }

    private void showLoading(boolean show) {
        binding.loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showLoginError(@Nullable String msg) {
        if (msg != null) {
            binding.tvLoginError.setText(msg);
            binding.tvLoginError.setVisibility(View.VISIBLE);
        } else {
            binding.tvLoginError.setVisibility(View.GONE);
        }
    }

    private void loadFileList(String path) {
        if (webDavClient == null) return;

        showLoading(true);
        binding.tvEmpty.setVisibility(View.GONE);

        executor.execute(() -> {
            if (isDestroyed.get()) return;
            try {
                List<CloudFile> files = webDavClient.listFiles(path);
                List<CloudFile> musicFiles = new ArrayList<>();
                List<CloudFile> dirs = new ArrayList<>();
                for (CloudFile f : files) {
                    if (f.isDirectory) {
                        dirs.add(f);
                    } else if (f.isAudioFile()) {
                        musicFiles.add(f);
                    }
                }

                Collections.sort(dirs, (a, b) -> a.name.compareToIgnoreCase(b.name));
                Collections.sort(musicFiles, (a, b) -> a.name.compareToIgnoreCase(b.name));

                List<CloudFile> merged = new ArrayList<>();
                if (!"/".equals(path)) {
                    CloudFile parent = new CloudFile();
                    parent.name = "..";
                    parent.href = getParentPath(path);
                    parent.isDirectory = true;
                    merged.add(parent);
                }
                merged.addAll(dirs);
                merged.addAll(musicFiles);

                mainHandler.post(() -> {
                    if (isDestroyed.get()) return;
                    currentPath = path;
                    currentFiles.clear();
                    currentFiles.addAll(merged);
                    adapter.notifyDataSetChanged();
                    binding.tvCurrentPath.setText(path);
                    binding.tvEmpty.setVisibility(currentFiles.isEmpty() ? View.VISIBLE : View.GONE);
                    showLoading(false);
                });

            } catch (IOException | XmlPullParserException e) {
                mainHandler.post(() -> {
                    if (isDestroyed.get()) return;
                    showLoading(false);
                    Toast.makeText(getActivity(), "加载失败: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String getParentPath(String path) {
        if ("/".equals(path)) return "/";
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash <= 0) return "/";
        return trimmed.substring(0, lastSlash);
    }

    private void navigateUp() {
        if (currentFiles.isEmpty() || "/".equals(currentPath)) return;
        String parent = getParentPath(currentPath);
        loadFileList(parent);
    }

    @Override
    public void onFileClick(int position) {
        if (position < 0 || position >= currentFiles.size()) return;
        CloudFile file = currentFiles.get(position);

        // Store auth headers for OkHttp interceptor to inject
        NowPlayingStore store = NowPlayingStore.getInstance();
        store.requestHeaders = webDavClient.getAuthHeaders();

        List<Song> songList = new ArrayList<>();
        int playIndex = -1;
        for (int i = 0; i < currentFiles.size(); i++) {
            CloudFile cf = currentFiles.get(i);
            if (cf.isDirectory) continue;
            // Use clean URL — auth is injected via OkHttp interceptor
            String url = webDavClient.getDirectUrl(cf.href);
            Song song = new Song(i, cf.name, cf.getFormattedSize(), url, 0);
            songList.add(song);
            if (i == position) {
                playIndex = songList.size() - 1;
            }
        }

        if (playIndex >= 0 && !songList.isEmpty()) {
            Intent intent = new Intent(getActivity(), PlayerActivity.class);
            intent.putParcelableArrayListExtra("songList", (ArrayList<Song>) songList);
            intent.putExtra("position", playIndex);
            startActivity(intent);
        } else {
            Toast.makeText(getActivity(), "无可播放的文件", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onFolderClick(int position) {
        if (position < 0 || position >= currentFiles.size()) return;
        CloudFile folder = currentFiles.get(position);
        if ("..".equals(folder.name)) {
            navigateUp();
        } else {
            String path = webDavClient.relativizePath(folder.href);
            if (!path.endsWith("/")) path = path + "/";
            loadFileList(path);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isDestroyed.set(true);
        executor.shutdownNow();
    }
}
