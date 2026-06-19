package com.ran.kk_music_player;

final class WebDavPathUtils {
    private WebDavPathUtils() {
    }

    static boolean isSamePath(String path, String href, String mountPath) {
        return normalizePath(path).equals(normalizePath(relativizePath(href, mountPath)));
    }

    static String relativizePath(String href, String mountPath) {
        if (href == null) return "/";
        if (mountPath != null && !mountPath.isEmpty() && !"/".equals(mountPath) && href.startsWith(mountPath)) {
            String relative = href.substring(mountPath.length());
            if (relative.isEmpty()) relative = "/";
            if (!relative.startsWith("/")) relative = "/" + relative;
            return relative;
        }
        if (!href.startsWith("/")) href = "/" + href;
        return href;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
