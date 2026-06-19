# Liquid Glass Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the bottom navigation background with an AndroidLiquidGlass liquid-glass floating island while keeping the existing `BottomNavigationView` text, icons, menu, and fragment switching above the effect.

**Architecture:** Add Compose support only for a focused background bridge: a `ComposeView` behind the XML `BottomNavigationView` renders a Kotlin composable using `io.github.kyant0:backdrop`. The existing Java `MainActivity` continues to own tab switching and only initializes the `ComposeView` content.

**Tech Stack:** Android Java, Kotlin Android plugin, Jetpack Compose `ComposeView`, `io.github.kyant0:backdrop:2.0.0`, Material `BottomNavigationView`, ViewBinding.

---

## File structure

- Modify `gradle/libs.versions.toml`: add Kotlin/Compose/plugin/library aliases for the smallest Compose bridge needed by the background view.
- Modify `app/build.gradle`: apply Kotlin Android and Compose compiler plugins, enable Compose, and add Compose/backdrop dependencies.
- Create `app/src/main/java/com/ran/kk_music_player/LiquidGlassNavBackground.kt`: a focused Kotlin file that exposes one Java-callable function to install Compose content into a `ComposeView`.
- Modify `app/src/main/res/layout/activity_main.xml`: replace the current `navViewBlur` plain `View` with a `ComposeView` background layer and keep `nav_view` as the top layer.
- Modify `app/src/main/java/com/ran/kk_music_player/MainActivity.java`: call the Kotlin installer for the background and stop applying native blur to the navigation background.
- Modify `app/src/main/res/values/themes.xml`: keep or strengthen the active indicator capsule style without changing labels or menu wiring.

---

### Task 1: Add Kotlin, Compose, and AndroidLiquidGlass dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle`

- [ ] **Step 1: Update the version catalog**

In `gradle/libs.versions.toml`, add these entries under `[versions]`:

```toml
kotlin = "2.3.21"
composeBom = "2026.06.00"
backdrop = "2.0.0"
```

Add these entries under `[libraries]`:

```toml
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
backdrop = { module = "io.github.kyant0:backdrop", version.ref = "backdrop" }
```

Add these entries under `[plugins]`:

```toml
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 2: Apply Kotlin and Compose plugins**

At the top of `app/build.gradle`, change the `plugins` block to:

```groovy
plugins {
    alias libs.plugins.android.application
    alias libs.plugins.kotlin.android
    alias libs.plugins.kotlin.compose
}
```

- [ ] **Step 3: Enable Compose build support**

In `app/build.gradle`, update the existing `buildFeatures` block to:

```groovy
buildFeatures {
    viewBinding true
    compose true
}
```

- [ ] **Step 4: Add dependencies**

In the `dependencies` block of `app/build.gradle`, add these lines near the other UI dependencies:

```groovy
implementation platform(libs.compose.bom)
implementation libs.compose.ui
implementation libs.compose.foundation
implementation libs.backdrop
```

- [ ] **Step 5: Build to verify dependency resolution**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: Gradle resolves Kotlin, Compose, and `io.github.kyant0:backdrop:2.0.0`. If this fails because `composeBom = "2026.06.00"` is unavailable, check Maven Central for the current Compose BOM version and replace only `composeBom` in `gradle/libs.versions.toml`.

- [ ] **Step 6: Commit dependency setup**

Run:

```bash
git add gradle/libs.versions.toml app/build.gradle
git commit -m "Add Compose backdrop dependencies"
```

---

### Task 2: Create the Compose liquid-glass navigation background bridge

**Files:**
- Create: `app/src/main/java/com/ran/kk_music_player/LiquidGlassNavBackground.kt`

- [ ] **Step 1: Create the Kotlin bridge file**

Create `app/src/main/java/com/ran/kk_music_player/LiquidGlassNavBackground.kt` with this content:

```kotlin
package com.ran.kk_music_player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

object LiquidGlassNavBackground {
    @JvmStatic
    fun install(composeView: ComposeView) {
        composeView.setContent {
            LiquidGlassNavIsland()
        }
    }
}

@Composable
private fun LiquidGlassNavIsland() {
    val backdrop = rememberCanvasBackdrop {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF1B2740),
                    Color(0xFF223B63),
                    Color(0xFF151A2A)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { androidx.compose.foundation.shape.RoundedCornerShape(32.dp) },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(16.dp.toPx(), 32.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = 0.16f))
                }
            )
            .drawBehind {
                drawRect(Color.White.copy(alpha = 0.06f))
            }
    )
}
```

- [ ] **Step 2: Compile Kotlin bridge**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: PASS. If the `lens` signature differs, open the installed source or official docs and update only the `lens(16.dp.toPx(), 32.dp.toPx())` call to the exact two-radius signature used by `io.github.kyant0:backdrop:2.0.0`.

- [ ] **Step 3: Commit the bridge**

Run:

```bash
git add app/src/main/java/com/ran/kk_music_player/LiquidGlassNavBackground.kt
git commit -m "Add liquid glass navigation background bridge"
```

---

### Task 3: Place the Compose glass island behind the existing navigation view

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml:132-163`

- [ ] **Step 1: Replace the navigation blur background view**

In `app/src/main/res/layout/activity_main.xml`, replace the current `navViewBlur` block:

```xml
<View
    android:id="@+id/navViewBlur"
    android:layout_width="0dp"
    android:layout_height="64dp"
    android:layout_marginStart="36dp"
    android:layout_marginEnd="36dp"
    android:layout_marginBottom="18dp"
    android:background="@drawable/bg_glass_island"
    android:clipToOutline="true"
    android:elevation="18dp"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent" />
```

with:

```xml
<androidx.compose.ui.platform.ComposeView
    android:id="@+id/navViewBlur"
    android:layout_width="0dp"
    android:layout_height="70dp"
    android:layout_marginStart="28dp"
    android:layout_marginEnd="28dp"
    android:layout_marginBottom="16dp"
    android:background="@drawable/bg_glass_island"
    android:clipToOutline="true"
    android:elevation="18dp"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent" />
```

- [ ] **Step 2: Align `BottomNavigationView` over the glass island**

In the same file, update the `nav_view` sizing attributes to:

```xml
<com.google.android.material.bottomnavigation.BottomNavigationView
    android:id="@+id/nav_view"
    android:layout_width="0dp"
    android:layout_height="70dp"
    android:layout_marginStart="28dp"
    android:layout_marginEnd="28dp"
    android:layout_marginBottom="16dp"
    android:background="@android:color/transparent"
    android:clipToOutline="true"
    android:elevation="20dp"
    app:itemActiveIndicatorStyle="@style/Widget.KKPlayer.GlassNavigation.ActiveIndicator"
    app:itemIconTint="@drawable/nav_item_color"
    app:itemTextColor="@drawable/nav_item_color"
    app:labelVisibilityMode="labeled"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:menu="@menu/bottom_nav_menu" />
```

Do not change `android:id="@+id/nav_view"`, `app:menu`, item tint, or label visibility.

- [ ] **Step 3: Build resource bindings**

Run:

```bash
./gradlew :app:compileDebugJavaWithJavac
```

Expected: PASS and `ActivityMainBinding` still contains `navViewBlur` and `navView`.

- [ ] **Step 4: Commit layout changes**

Run:

```bash
git add app/src/main/res/layout/activity_main.xml
git commit -m "Layer liquid glass island behind navigation"
```

---

### Task 4: Initialize the glass background from MainActivity without changing navigation logic

**Files:**
- Modify: `app/src/main/java/com/ran/kk_music_player/MainActivity.java:104-106`
- Modify: `app/src/main/java/com/ran/kk_music_player/MainActivity.java:224-230`

- [ ] **Step 1: Install the Compose background after binding inflation**

In `MainActivity.java`, after the existing call to `applyNativeBlur();`, add:

```java
        LiquidGlassNavBackground.install(binding.navViewBlur);
```

The surrounding block should become:

```java
        applyNativeBlur();
        LiquidGlassNavBackground.install(binding.navViewBlur);

        binding.navView.setOnItemSelectedListener(item -> {
```

- [ ] **Step 2: Stop applying native blur to the Compose navigation background**

In `MainActivity.java`, change `applyNativeBlur()` from:

```java
    private void applyNativeBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect blur = RenderEffect.createBlurEffect(70f, 70f, Shader.TileMode.CLAMP);
            binding.miniPlayerBlur.setRenderEffect(blur);
            binding.navViewBlur.setRenderEffect(blur);
        }
    }
```

to:

```java
    private void applyNativeBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RenderEffect blur = RenderEffect.createBlurEffect(70f, 70f, Shader.TileMode.CLAMP);
            binding.miniPlayerBlur.setRenderEffect(blur);
        }
    }
```

Do not change the `binding.navView.setOnItemSelectedListener` block.

- [ ] **Step 3: Compile Java and Kotlin together**

Run:

```bash
./gradlew :app:compileDebugKotlin :app:compileDebugJavaWithJavac
```

Expected: PASS. Java can call `LiquidGlassNavBackground.install(binding.navViewBlur)` because the Kotlin object method is annotated with `@JvmStatic`.

- [ ] **Step 4: Commit MainActivity initialization**

Run:

```bash
git add app/src/main/java/com/ran/kk_music_player/MainActivity.java
git commit -m "Initialize liquid glass navigation background"
```

---

### Task 5: Strengthen selected-item capsule while keeping labels readable

**Files:**
- Modify: `app/src/main/res/values/themes.xml:10-13`
- Modify: `app/src/main/res/drawable/bg_glass_nav_item.xml`

- [ ] **Step 1: Keep the style pointing at the active indicator drawable**

Verify `themes.xml` still contains:

```xml
<style name="Widget.KKPlayer.GlassNavigation.ActiveIndicator" parent="Widget.Material3.BottomNavigationView.ActiveIndicator">
    <item name="android:color">@android:color/transparent</item>
    <item name="android:drawable">@drawable/bg_glass_nav_item</item>
</style>
```

- [ ] **Step 2: Strengthen the active indicator drawable**

Set `app/src/main/res/drawable/bg_glass_nav_item.xml` to:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <corners android:radius="28dp" />
    <solid android:color="#33FFFFFF" />
    <stroke
        android:width="1dp"
        android:color="#55FFFFFF" />
    <padding
        android:left="12dp"
        android:top="6dp"
        android:right="12dp"
        android:bottom="6dp" />
</shape>
```

- [ ] **Step 3: Compile resources**

Run:

```bash
./gradlew :app:mergeDebugResources
```

Expected: PASS.

- [ ] **Step 4: Commit selected-state polish**

Run:

```bash
git add app/src/main/res/values/themes.xml app/src/main/res/drawable/bg_glass_nav_item.xml
git commit -m "Polish glass navigation selected state"
```

---

### Task 6: Verify build and UI behavior

**Files:**
- No source changes expected unless verification reveals a concrete issue.

- [ ] **Step 1: Run unit/build verification**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Launch the app**

Run:

```bash
./gradlew :app:installDebug
```

Expected: install succeeds on the connected emulator/device.

- [ ] **Step 3: Manually verify navigation behavior**

Open the app and verify:

- The bottom navigation is a strong liquid-glass floating pill.
- `音乐`, `云盘`, and `设置` labels are readable and not blurred.
- Tapping each tab switches to the corresponding fragment.
- The selected item shows the strengthened glass capsule.

- [ ] **Step 4: Manually verify mini player spacing**

Start playback or enter a state where the mini player is visible. Verify:

- The mini player remains above the navigation island.
- The mini player does not overlap the navigation labels or active capsule.
- The navigation remains tappable while the mini player is visible.

- [ ] **Step 5: Fix only verified issues**

If a specific issue is observed, make the smallest matching edit:

- If labels are too close to the island edge, increase `nav_view` and `navViewBlur` height from `70dp` to `74dp` in `activity_main.xml`.
- If the island overlaps the mini player, change both `layout_marginBottom="16dp"` values for `navViewBlur` and `nav_view` back to `18dp` in `activity_main.xml`.
- If the active capsule is too bright, reduce `bg_glass_nav_item.xml` solid color from `#33FFFFFF` to `#26FFFFFF`.

Run `./gradlew :app:assembleDebug` again after any fix.

- [ ] **Step 6: Commit verification fixes if any**

If Step 5 changed files, run:

```bash
git add app/src/main/res/layout/activity_main.xml app/src/main/res/drawable/bg_glass_nav_item.xml
git commit -m "Tune liquid glass navigation spacing"
```

If Step 5 did not change files, do not create an empty commit.
