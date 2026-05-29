# Takit

Takit is an Android MVP for quickly sorting screenshots and camera photos into selected Gallery folders.

## Features

- Create app-managed Gallery folders under `DCIM/Takit/...`.
- Sync existing `DCIM/...` image folders when media-read permission is granted.
- Persist the latest selected save folder across app restarts.
- Enable a floating overlay bubble with quick actions for screenshot, camera capture, opening the app, and switching favorite folders.
- Adjust the floating bubble opacity and use a smaller circular bubble mark.
- Save camera captures directly into the selected MediaStore folder.
- Capture screenshots through Android's normal MediaProjection consent flow, keep the approved session alive, and reuse it for later bubble captures.
- Register frequently used folders for faster switching from the bubble without showing the full folder search UI.

## Build

```bash
gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

The release APK is generated at:

```text
app/build/outputs/apk/release/app-release.apk
```

## Android Notes

- Silent first-time background screenshots are intentionally unsupported because Android requires MediaProjection user consent before a capture session starts.
- Takit reuses an active MediaProjection session so later bubble screenshots can capture the screen behind the bubble without reopening the Takit app. If Android stops the session, the next screenshot asks for consent again.
- On Android 14+, Takit requests default-display capture to reduce app-only sharing confusion where supported, but the first system consent screen itself is still required by Android.
- The floating bubble requires the Draw over other apps permission.
- Gallery writes use MediaStore with `RELATIVE_PATH`; the MVP supports Android 10/API 29 and newer.
- New app-created folders default under `DCIM/Takit/...`; synced gallery folders are limited to `DCIM/...` albums visible through MediaStore permissions.
