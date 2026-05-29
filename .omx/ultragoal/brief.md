# Ultragoal Brief: Takit Post-Install Fixes

Fix the installed Takit Android MVP based on real device feedback.

User-reported problems:
- Screenshot captures a transition screen after tapping the bubble, and Android shows app-screen/full-screen sharing choices that are confusing.
- Folder import/selection does not match real gallery albums under paths such as `/storage/emulated/0/DCIM/...`; existing gallery folder listing is not useful enough.
- Camera capture reaches the camera confirmation UI, but confirmed photos do not appear in Gallery, and a later attempt can make the camera app fail.
- The bubble folder button only shows a toast and should let the user choose the target folder.
- Replace the app icon and bubble mark with Codex-generated visual assets.

Constraints:
- Silent screenshots without Android consent are not available to normal apps; keep the MediaProjection consent flow and reduce avoidable confusion/timing problems.
- Continue using platform APIs and MediaStore.
- Verify with targeted tests and Android build/lint.
