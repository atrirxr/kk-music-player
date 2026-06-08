package com.ran.kk_music_player;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
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

import com.ran.kk_music_player.WebDavClient.CloudFile;
import com.ran.kk_music_player.databinding.FragmentCloudBinding;

import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class CloudFragment extends Fragment implements CloudSongAdapter.OnItemClickListener {

    private FragmentCloudBinding binding;
    private WebDavClient webDavClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService preloadExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isDestroyed = new AtomicBoolean(false);
    private final AtomicBoolean preloadActive = new AtomicBoolean(false);
    private final ConcurrentMap<String, String[]> fileMetadata = new ConcurrentHashMap<>();
    private volatile int sortGeneration = 0;

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

    private void stopPreload() {
        preloadActive.set(false);
    }

    private void disconnect() {
        stopPreload();
        CoverCache.clear();
        fileMetadata.clear();
        clearLocalMetadataCache(requireContext().getCacheDir());
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

    /**
     * 在后台线程依次缓存当前目录中所有音乐文件的封面。
     * 提取专辑标签做去重：同专辑歌曲跳过已缓存的封面。
     */
    private void startPreloadCovers() {
        if (currentFiles.isEmpty()) return;
        preloadActive.set(true);
        List<CloudFile> snapFiles = new ArrayList<>(currentFiles);
        final File appCacheDir = requireContext().getCacheDir();
        final File cacheDir = new File(appCacheDir, "album_covers");

        final int gen = sortGeneration;
        preloadExecutor.execute(() -> {
            if (!preloadActive.get() || isDestroyed.get()) return;

            for (int i = 0; i < snapFiles.size(); i++) {
                if (!preloadActive.get() || isDestroyed.get()) break;

                CloudFile f = snapFiles.get(i);
                if (f.isDirectory || f.directUrl == null) continue;

                try {
                    String urlHash = String.valueOf(f.directUrl.hashCode());
                    File cacheFile = new File(cacheDir, urlHash + ".jpg");

                    if (cacheFile.exists()) continue;

                    byte[] header = webDavClient.downloadAudioHeader(f.href, 2097152);
                    if (header == null || header.length == 0) continue;

                    File tempFile = new File(cacheDir, "preload_" + urlHash + ".tmp");
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        fos.write(header);
                    }

                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    mmr.setDataSource(tempFile.getAbsolutePath());
                    String album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                    String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    byte[] art = mmr.getEmbeddedPicture();
                    mmr.release();

                    String albumKey = CoverCache.buildAlbumKey(artist, album);

                    if (albumKey != null && CoverCache.hasAlbum(albumKey)) {
                        String cachedHash = CoverCache.getAlbumUrlHash(albumKey);
                        File cachedCover = new File(cacheDir, cachedHash + ".jpg");
                        if (cachedCover.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            cacheDir.mkdirs();
                            try (InputStream src = new FileInputStream(cachedCover);
                                 FileOutputStream dst = new FileOutputStream(cacheFile)) {
                                byte[] buf = new byte[8192];
                                int n;
                                while ((n = src.read(buf)) != -1) dst.write(buf, 0, n);
                            }
                        }
                    } else if (art != null) {
                        //noinspection ResultOfMethodCallIgnored
                        cacheDir.mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                            fos.write(art);
                        }
                        if (albumKey != null) {
                            CoverCache.registerAlbum(albumKey, urlHash);
                        }
                    }

                    fileMetadata.put(urlHash, new String[]{
                            artist != null ? artist : "",
                            album != null ? album : ""
                    });

                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();

                    int idx = currentFiles.indexOf(f);
                    if (idx >= 0) {
                        int finalIdx = idx;
                        mainHandler.post(() -> {
                            if (!preloadActive.get() || isDestroyed.get()) return;
                            adapter.notifyItemChanged(finalIdx);
                        });
                    }

                } catch (Exception ignored) {
                }
            }

            // 封面缓存完成后，启动排序（全量提取缺失元数据）
            if (preloadActive.get() && !isDestroyed.get() && gen == sortGeneration) {
                mainHandler.post(() -> {
                    if (!preloadActive.get() || isDestroyed.get() || gen != sortGeneration) return;
                    sortByArtistAlbumAsync(gen, true, appCacheDir);
                });
            }
        });
    }

    /**
     * 封面缓存完成后执行：提取缺失的元数据 → TimSort 按歌手/专辑/文件名排序 → 更新 UI。
     * 使用生成号防止目录切换导致的竞态。
     */
    private void sortByArtistAlbumAsync(int gen, boolean extractMissing, File cacheDir) {
        if (webDavClient == null) return;
        final String savePath = currentPath;

        executor.execute(() -> {
            if (isDestroyed.get() || gen != sortGeneration) return;

            // 第一阶段：为尚未提取元数据的文件下载 256KB 头部（仅在 extractMissing 时执行）
            if (extractMissing) {
                for (CloudFile f : currentFiles) {
                    if (isDestroyed.get() || gen != sortGeneration) break;
                    if (f.isDirectory || f.directUrl == null) continue;
                    String hash = String.valueOf(f.directUrl.hashCode());
                    if (fileMetadata.containsKey(hash)) continue;

                    try {
                        byte[] header = webDavClient.downloadAudioHeader(f.href, 262144);
                        if (header == null || header.length == 0) continue;

                        File tempFile = File.createTempFile("meta_", ".tmp", cacheDir);
                        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                            fos.write(header);
                        }

                        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                        mmr.setDataSource(tempFile.getAbsolutePath());
                        String album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                        String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                        mmr.release();
                        //noinspection ResultOfMethodCallIgnored
                        tempFile.delete();

                        fileMetadata.put(hash, new String[]{
                                artist != null ? artist : "",
                                album != null ? album : ""
                        });
                    } catch (Exception ignored) {
                    }
                }
            }

            if (isDestroyed.get() || gen != sortGeneration) return;

            // 第二阶段：TimSort — 歌手 ↗ 专辑 ↗ 文件名（不分区大小写）
            List<CloudFile> sorted = new ArrayList<>(currentFiles);
            Collections.sort(sorted, (a, b) -> {
                if ("..".equals(a.name)) return -1;
                if ("..".equals(b.name)) return 1;

                if (a.isDirectory && !b.isDirectory) return -1;
                if (!a.isDirectory && b.isDirectory) return 1;
                if (a.isDirectory) return a.name.compareToIgnoreCase(b.name);

                String[] metaA = fileMetadata.get(String.valueOf(a.directUrl.hashCode()));
                String[] metaB = fileMetadata.get(String.valueOf(b.directUrl.hashCode()));

                String artistA = metaA != null ? metaA[0] : "";
                String artistB = metaB != null ? metaB[0] : "";

                int cmp;
                if (artistA.isEmpty() && !artistB.isEmpty()) cmp = 1;
                else if (!artistA.isEmpty() && artistB.isEmpty()) cmp = -1;
                else cmp = artistA.compareToIgnoreCase(artistB);
                if (cmp != 0) return cmp;

                String albumA = metaA != null ? metaA[1] : "";
                String albumB = metaB != null ? metaB[1] : "";

                if (albumA.isEmpty() && !albumB.isEmpty()) cmp = 1;
                else if (!albumA.isEmpty() && albumB.isEmpty()) cmp = -1;
                else cmp = albumA.compareToIgnoreCase(albumB);
                if (cmp != 0) return cmp;

                return a.name.compareToIgnoreCase(b.name);
            });

            // 排序完成后持久化缓存
            saveMetadataCache(savePath, cacheDir);

            mainHandler.post(() -> {
                if (isDestroyed.get() || gen != sortGeneration) return;
                currentFiles.clear();
                currentFiles.addAll(sorted);
                adapter.notifyDataSetChanged();
                binding.tvEmpty.setVisibility(currentFiles.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    // ---- 元数据本地缓存（Tab 分隔行格式：urlHash\tartist\talbum） ----

    private File getMetaCacheFile(String path, File cacheDir) {
        String hash = String.valueOf(("cloud_meta_" + path).hashCode());
        return new File(cacheDir, "cloud_meta/" + hash + ".txt");
    }

    private void loadMetadataCache(String path, File cacheDir) {
        File f = getMetaCacheFile(path, cacheDir);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length == 3) {
                    fileMetadata.put(parts[0], new String[]{parts[1], parts[2]});
                }
            }
        } catch (Exception ignored) {}
    }

    private void saveMetadataCache(String path, File cacheDir) {
        if (fileMetadata.isEmpty()) return;
        File f = getMetaCacheFile(path, cacheDir);
        //noinspection ResultOfMethodCallIgnored
        f.getParentFile().mkdirs();
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"))) {
            for (java.util.Map.Entry<String, String[]> e : fileMetadata.entrySet()) {
                bw.write(e.getKey());
                bw.write('\t');
                bw.write(e.getValue()[0]);
                bw.write('\t');
                bw.write(e.getValue()[1]);
                bw.write('\n');
            }
        } catch (Exception ignored) {}
    }

    private void clearLocalMetadataCache(File cacheDir) {
        File dir = new File(cacheDir, "cloud_meta");
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) //noinspection ResultOfMethodCallIgnored
                    f.delete();
            }
        }
    }

    private void loadFileList(String path) {
        if (webDavClient == null) return;

        stopPreload();
        fileMetadata.clear();
        sortGeneration++;

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
                        f.directUrl = webDavClient.getDirectUrl(f.href);
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

                    // 从缓存加载元数据 → 立即排序（免网络）
                    loadMetadataCache(path, requireContext().getCacheDir());
                    if (!fileMetadata.isEmpty()) {
                        sortByArtistAlbumAsync(sortGeneration, false, requireContext().getCacheDir());
                    }

                    // 进入目录后开始预缓存封面
                    startPreloadCovers();
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
        store.webDavClient = webDavClient;

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
        stopPreload();
        isDestroyed.set(true);
        executor.shutdownNow();
        preloadExecutor.shutdownNow();
    }
}
