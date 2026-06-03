# Mini-Player Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a mini-player info panel above the bottom navigation bar in MainActivity showing currently playing song info, playback progress, album art, and a click target to open PlayerActivity without restarting the same song.

**Architecture:** Create a `NowPlayingStore` singleton to share playback state between MainActivity and PlayerActivity. MainActivity connects to MusicService's MediaSession via MediaController. Mini-player bar is part of activity_main.xml layout, positioned between fragment_container and nav_view.

**Tech Stack:** Android (Java), Media3/ExoPlayer, Glide, ConstraintLayout

---

### Task 1: NowPlayingStore singleton

**Files:**
- Create: `app/src/main/java/com/shahadot/android_music_player/NowPlayingStore.java`

- [ ] **Create NowPlayingStore.java**

```java
package com.shahadot.android_music_player;

public class NowPlayingStore {
    private static NowPlayingStore instance;

    public Song currentSong;
    public boolean isPlaying;
    public long position;
    public long duration;

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
    }
}
```

### Task 2: Update activity_main.xml with mini-player layout

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`

- [ ] **Add mini-player layout above nav_view**

Insert a horizontal mini-player bar between `fragment_container` (bottom constraint) and `nav_view` (top constraint). The mini-player contains: small album art (48dp circle), title/artist text, progress bar (SeekBar), elapsed/duration text. Starts invisible, shown when a song is playing.

```xml
<androidx.constraintlayout.widget.ConstraintLayout
    android:id="@+id/miniPlayer"
    android:layout_width="0dp"
    android:layout_height="56dp"
    android:background="?attr/colorSurface"
    android:visibility="gone"
    android:clickable="true"
    android:focusable="true"
    app:layout_constraintBottom_toTopOf="@+id/nav_view"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent">

    <ImageView
        android:id="@+id/miniAlbumArt"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:layout_marginStart="12dp"
        android:scaleType="centerCrop"
        android:contentDescription="Album art"
        android:background="@drawable/circle_bg"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent" />

    <TextView
        android:id="@+id/miniTitle"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_marginEnd="8dp"
        android:maxLines="1"
        android:ellipsize="end"
        android:textSize="14sp"
        android:textStyle="bold"
        android:textColor="@color/white"
        app:layout_constraintStart_toEndOf="@+id/miniAlbumArt"
        app:layout_constraintEnd_toStartOf="@+id/miniElapsed"
        app:layout_constraintTop_toTopOf="@+id/miniAlbumArt"
        app:layout_constraintBottom_toTopOf="@+id/miniProgress" />

    <SeekBar
        android:id="@+id/miniProgress"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:max="1000"
        android:progress="0"
        android:thumb="@null"
        android:progressDrawable="@drawable/progress_bar_bg"
        app:layout_constraintStart_toStartOf="@+id/miniTitle"
        app:layout_constraintEnd_toEndOf="@+id/miniTitle"
        app:layout_constraintTop_toBottomOf="@+id/miniTitle"
        app:layout_constraintBottom_toBottomOf="@+id/miniAlbumArt" />

    <TextView
        android:id="@+id/miniElapsed"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="12dp"
        android:textSize="12sp"
        android:textColor="@color/white"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintTop_toTopOf="@+id/miniAlbumArt"
        app:layout_constraintBottom_toBottomOf="@+id/miniTitle" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

Also update `fragment_container` bottom constraint to attach to `miniPlayer` top instead of `nav_view` top.

- [ ] **Create progress_bar_bg.xml drawable**

**Files:**
- Create: `app/src/main/res/drawable/progress_bar_bg.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background">
        <shape>
            <solid android:color="#33FFFFFF" />
            <corners android:radius="2dp" />
        </shape>
    </item>
    <item android:id="@android:id/progress">
        <clip>
            <shape>
                <solid android:color="@color/white" />
                <corners android:radius="2dp" />
            </shape>
        </clip>
    </item>
</layer-list>
```

### Task 3: Update MainActivity to connect to MediaSession and drive mini-player

**Files:**
- Modify: `app/src/main/java/com/shahadot/android_music_player/MainActivity.java`

- [ ] **Expand MainActivity with MediaController connection and mini-player logic**

Add fields: `MediaController mediaController`, `Handler handler`, `Runnable updateRunnable`.
In `onCreate` after the nav setup: connect to MusicService MediaSession, wire mini-player click to open PlayerActivity.
The update runnable polls position from MediaController and updates SeekBar + elapsed time.
When a song starts playing, populate NowPlayingStore and show mini-player.
When playback ends or stops, hide mini-player.

Key aspects:
- Mini-player click → Intent with `EXTRA_OPEN_ONLY=true` flag
- `onDestroy` releases MediaController
- The MediaController is built asynchronously with ListenableFuture

### Task 4: Update PlayerActivity to support "open only" mode

**Files:**
- Modify: `app/src/main/java/com/shahadot/android_music_player/PlayerActivity.java`

- [ ] **Add open-only mode support**

- Add `EXTRA_OPEN_ONLY = "open_only"` constant
- In `onCreate`, check for this extra. If true AND NowPlayingStore has a currentSong already set, skip `initPlayerWithSong` and just connect to existing session
- Update `NowPlayingStore` in `updateUI()`, `updatePlayPauseIcon()`, and the update runnable
- On any song change (playNext/playPrev), update NowPlayingStore

### Task 5: Update AndroidManifest.xml

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Set PlayerActivity launchMode to singleTop**

```xml
<activity
    android:name=".PlayerActivity"
    android:exported="false"
    android:launchMode="singleTop" />
```

### Task 6: Add string resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Add any new string resources needed**

(If needed, added during implementation)
