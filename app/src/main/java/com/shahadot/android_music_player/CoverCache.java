package com.shahadot.android_music_player;

import java.util.concurrent.ConcurrentHashMap;

public class CoverCache {
    private static final ConcurrentHashMap<String, String> albumCoverMap = new ConcurrentHashMap<>();

    public static String getAlbumUrlHash(String albumKey) {
        return albumCoverMap.get(albumKey);
    }

    public static boolean hasAlbum(String albumKey) {
        return albumCoverMap.containsKey(albumKey);
    }

    public static void registerAlbum(String albumKey, String urlHash) {
        albumCoverMap.putIfAbsent(albumKey, urlHash);
    }

    public static String buildAlbumKey(String artist, String album) {
        if (album == null || album.trim().isEmpty()) return null;
        String a = artist != null && !artist.trim().isEmpty() ? artist.trim() : "Unknown";
        return a + " ⏑ " + album.trim();
    }

    public static void clear() {
        albumCoverMap.clear();
    }
}
