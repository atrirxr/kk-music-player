package com.ran.kk_music_player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebDavClientTest {
    @Test
    public void isSamePathMatchesCurrentDirectoryHrefUnderMountPath() {
        assertTrue(WebDavPathUtils.isSamePath("/music/", "/dav/music/", "/dav"));
        assertFalse(WebDavPathUtils.isSamePath("/music/", "/dav/music/album/", "/dav"));
    }

    @Test
    public void isSamePathIgnoresTrailingSlashDifference() {
        assertTrue(WebDavPathUtils.isSamePath("/music", "/dav/music/", "/dav"));
        assertTrue(WebDavPathUtils.isSamePath("/music/", "/dav/music", "/dav"));
    }
}
