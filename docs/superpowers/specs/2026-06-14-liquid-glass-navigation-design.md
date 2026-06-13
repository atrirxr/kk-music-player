# Liquid Glass Navigation Design

## Goal

Refactor the bottom navigation into a strong liquid-glass floating island using AndroidLiquidGlass while keeping the navigation text and icons visually above the glass effect.

## Current context

`activity_main.xml` already uses a bottom `BottomNavigationView` over a separate `navViewBlur` background view. `MainActivity` applies a native blur effect to `navViewBlur` on Android 12+ and handles fragment switching through the existing `setOnItemSelectedListener`. JitPack is already configured in `settings.gradle`, but AndroidLiquidGlass is not yet declared in the version catalog.

## Chosen approach

Use AndroidLiquidGlass as the background island container and keep `BottomNavigationView` as the top interactive layer.

The bottom navigation area will have three visual layers:

1. AndroidLiquidGlass floating island background.
2. A compatible rounded shadow/stroke backing only if needed for contrast or older-device fallback.
3. Transparent `BottomNavigationView` above the glass, continuing to render icons, labels, active state, and touch handling.

This keeps the library effect scoped to the island background instead of applying it to text-bearing views.

## Dependency integration

Add AndroidLiquidGlass to `gradle/libs.versions.toml` and reference it from `app/build.gradle` through the version catalog. The repository already permits JitPack, so no repository change is expected unless the library requires a different Maven source.

## Layout changes

In `activity_main.xml`, replace or wrap the current `navViewBlur` background layer with the AndroidLiquidGlass view/container required by the library. Preserve the existing `BottomNavigationView` ID, menu, item tint, label visibility, and constraints so view binding and navigation logic stay stable.

The island can become wider, taller, and visually heavier than the current conservative glass style to match the selected strong-effect direction. The mini player remains constrained above `nav_view`, so it should continue to sit above the navigation island.

## MainActivity behavior

Keep the existing fragment switching logic unchanged. Only adjust `applyNativeBlur()` if the old native blur conflicts with the AndroidLiquidGlass background. If AndroidLiquidGlass needs runtime setup, bind only the background island view and do not include the `BottomNavigationView` text/icon layer in the glass processing target.

## Visual requirements

- Bottom navigation appears as a strong liquid-glass floating pill.
- Active item can use a more visible glass capsule style.
- Navigation labels and icons remain clear, readable, and above the effect.
- The glass effect must not blur or distort the labels `音乐`, `云盘`, or `设置`.
- Mini player remains visually separate and above the navigation area when visible.

## Testing

- Build the app successfully.
- Launch the app and inspect the bottom navigation.
- Switch between Music, Cloud, and Settings tabs.
- Start playback or otherwise show the mini player and verify it does not overlap the navigation island incorrectly.
- Confirm labels and icons remain readable in normal and selected states.
