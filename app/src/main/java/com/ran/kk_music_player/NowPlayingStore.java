package com.ran.kk_music_player;

import java.util.List;
import java.util.Map;

public class NowPlayingStore {
    private static NowPlayingStore instance;

    public Song currentSong;
    public boolean isPlaying;
    public long position;
    public long duration;
    public List<Song> songList;
    public int currentIndex;
    public boolean isSequential = true;
    public boolean isShuffle = false;
    public Map<String, String> requestHeaders;
    public WebDavClient webDavClient;

    private NowPlayingStore() {}

    public static synchronized NowPlayingStore getInstance() {
        if (instance == null) {
            instance = new NowPlayingStore();
        }
        return instance;
    }

    public void reset() {
        currentSong = null;
        isPlaying = false;
        position = 0;
        duration = 0;
        songList = null;
        currentIndex = 0;
    }
}
