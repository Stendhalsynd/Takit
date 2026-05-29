#!/bin/zsh
set -euo pipefail

ROOT="/Users/jihun/StudioProjects/Takit"
LOG_DIR="$ROOT/.omx/logs"
LOG_FILE="$LOG_DIR/release-apk-hook.log"
mkdir -p "$LOG_DIR"
exec >> "$LOG_FILE" 2>&1

echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] release hook start"

if [[ "${TAKIT_RELEASE_HOOK_DISABLE:-}" == "1" ]]; then
  echo "release hook disabled by TAKIT_RELEASE_HOOK_DISABLE"
  exit 0
fi

cd "$ROOT"

remote_url="$(git config --get remote.origin.url 2>/dev/null || true)"
if [[ -z "$remote_url" ]]; then
  echo "skip: no GitHub remote configured"
  exit 0
fi

branch="$(git branch --show-current 2>/dev/null || true)"
if [[ -z "$branch" ]]; then
  echo "skip: detached HEAD"
  exit 0
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "skip: working tree has uncommitted changes"
  exit 0
fi

version="$(awk -F'"' '/versionName/ {print $2; exit}' app/build.gradle)"
if [[ -z "$version" ]]; then
  echo "skip: versionName not found"
  exit 0
fi

tag="v$version"
if gh release view "$tag" >/dev/null 2>&1; then
  echo "skip: GitHub Release already exists for $tag"
  exit 0
fi

if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
  tagged_commit="$(git rev-list -n 1 "$tag")"
  head_commit="$(git rev-parse HEAD)"
  if [[ "$tagged_commit" != "$head_commit" ]]; then
    echo "skip: local tag $tag points to $tagged_commit, not HEAD $head_commit"
    exit 0
  fi
else
  gradle :app:testDebugUnitTest :app:assembleRelease
  git tag "$tag"
fi

apk="$ROOT/app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$apk" ]]; then
  gradle :app:assembleRelease
fi

sha256="$(shasum -a 256 "$apk" | awk '{print $1}')"
previous_tag="$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || true)"
if [[ -n "$previous_tag" ]]; then
  change_lines="$(git log --format='- %s' "$previous_tag"..HEAD)"
else
  change_lines="$(git log --format='- %s' --max-count=10 HEAD)"
fi
if [[ -z "$change_lines" ]]; then
  change_lines="- Release APK build for $tag"
fi

git push origin "$branch"
git push origin "$tag"

release_notes="$(printf '%s\n\nAPK SHA-256: %s\n' "$change_lines" "$sha256")"
gh release create "$tag" "$apk#Takit release APK" --title "Takit $tag" --notes "$release_notes"

env_file="/Users/jihun/StudioProjects/dash/.env"
if [[ -f "$env_file" ]]; then
  source "$env_file"
  webhook_url="${DISCORD_WEBHOOK_URL:-${OMC_DISCORD_WEBHOOK_URL:-}}"
  if [[ -n "$webhook_url" ]]; then
    repo_url="$remote_url"
    if [[ "$repo_url" == git@github.com:* ]]; then
      repo_url="https://github.com/${repo_url#git@github.com:}"
    fi
    repo_url="${repo_url%.git}"
    release_url="$repo_url/releases/tag/$tag"
    commit="$(git rev-parse --short HEAD)"
    msg="$(printf '[Takit] Release APK 자동 배포\n\n- 커밋: %s\n- 변경 요약\n  - %s\n- 버전: %s\n- 릴리스: %s\n' "$commit" "${change_lines//$'\n'/$'\n  - '}" "$tag" "$release_url")"
    payload="$(python3 -c 'import json,sys; print(json.dumps({"content": sys.stdin.read()}))' <<< "$msg")"
    http_code="$(curl -sS -o /tmp/takit_discord_hook_resp.txt -w "%{http_code}" -H "Content-Type: application/json" -d "$payload" "$webhook_url")"
    echo "discord HTTP_CODE=$http_code"
  else
    echo "skip discord: webhook env var not found"
  fi
else
  echo "skip discord: env file not found"
fi

echo "release hook complete: $tag $sha256"
