# Takit

Takit is an Android MVP for quickly sorting screenshots and camera photos into selected Gallery folders.

## Features

- Create app-managed Gallery folders under `DCIM/Takit/...`.
- Import existing `DCIM/...` and `Pictures/...` image folders when media-read permission is granted.
- Persist the latest selected save folder across app restarts.
- Enable a floating overlay bubble with quick actions for screenshot and camera capture.
- Save camera captures directly into the selected MediaStore folder.
- Capture screenshots through Android's normal MediaProjection consent flow, then save the image into the selected folder.

## Build

```bash
gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

The release APK is generated at:

```text
app/build/outputs/apk/release/app-release.apk
```

## Android Notes

- Silent background screenshots are intentionally unsupported. Takit uses Android's system MediaProjection consent prompt for each screenshot session.
- On Android 14+, Takit requests default-display capture to avoid the app-only sharing option where supported, but the system consent screen itself is still required by Android.
- The floating bubble requires the Draw over other apps permission.
- Gallery writes use MediaStore with `RELATIVE_PATH`; the MVP supports Android 10/API 29 and newer.
- New app-created folders default under `DCIM/Takit/...`; imported folders can include existing `DCIM/...` and `Pictures/...` gallery albums visible through MediaStore permissions.
