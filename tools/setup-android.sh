#!/usr/bin/env bash
# Makes a fresh Claude Code on the web container able to build and test Vigilant, WITHOUT touching
# Maven Central (BRIEF.md build trap 6). Idempotent and self-contained (needs no other file from
# this repo, so it can run straight from GitHub). It never fails: a cloud environment whose setup
# script exits non-zero can't start a session at all, so a step that can't finish prints WARN and
# the script still exits 0 (bootstrap.sh then reports the missing SDK at session start).
#
# Each account's cloud environment runs it as its Setup script, one line (BRIEF.md build trap 6):
#   curl -fsSL https://raw.githubusercontent.com/tjshea90/novig/main/tools/setup-android.sh | bash -s -- --prewarm
# In a session, `bash tools/setup-android.sh` repairs a container that didn't run it (no --prewarm
# there: the session's first build downloads what it uses anyway).
#
# Why Maven Central is avoided (researched 2026-09-27, BRIEF.md trap 6): Central rate-limits by
# EGRESS IP, on the aggregate traffic it sees from that IP. Every cloud session leaves through a
# small pool of shared egress addresses and starts with an empty ~/.gradle and ~/.m2 (a full
# Vigilant build pulls ~1.5 GB, thousands of requests), so the pool keeps crossing Central's
# threshold and gets 429 "Too Many Requests" for everyone on it, for minutes to hours (blocks
# escalate on repeat offenders). Nothing one account does changes that IP's total, and retrying
# makes it worse. Google's public mirror of Maven Central (Cloud Storage, not rate-limited this
# way) serves the same artifacts, so:
#   1. Gradle: an init script puts the mirror FIRST for plugins and dependencies (Gradle stops at
#      the first repository that errors, so a 429 never falls through to the next one).
#   2. Robolectric: it downloads its android-all jars itself at test time, outside Gradle (the
#      mirror in 1 doesn't cover it). `vigilant.mavenMirror` in ~/.gradle/gradle.properties makes
#      app/build.gradle.kts hand the mirror to Robolectric; CI never sets it and keeps Central.
#   3. The Android SDK from dl.google.com into /opt/android-sdk: platform 36, build-tools 35.0.0
#      (AGP 8.13's default, the one the build uses) and 36.0.0. dl.google.com is NOT on the cloud
#      environments' default "Trusted" network list: the environment has to allow it.
#   4. --prewarm: builds a throwaway copy of the app and runs one Robolectric test, so every Gradle
#      dependency and Robolectric's Android jar are on disk. A setup script that finishes within
#      ~5 minutes is snapshotted and reused by new sessions for ~7 days, so they start with all of
#      it: the full test floor took 210 s from empty caches and 110 s with them (2026-09-29).
#      It gets what's left of a 250 s budget for the whole script (the SDK download alone took
#      13-65 s), so the script stays under those 5 minutes even on a slow day.
set -uo pipefail
SCRIPT_START=$(date +%s)

MIRROR="https://maven-central.storage-download.googleapis.com/maven2/"
SDK="${ANDROID_HOME:-/opt/android-sdk}"
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"
REPO_URL="https://github.com/tjshea90/novig.git"
PREWARM=0
[ "${1:-}" = "--prewarm" ] && PREWARM=1
warn() { echo "  WARN  $*"; }

# ---- 1. Gradle: Google's Maven Central mirror first ------------------------------------------
if mkdir -p "$GRADLE_HOME_DIR/init.d" && cat > "$GRADLE_HOME_DIR/init.d/mirror.gradle.kts" <<EOF
// Written by tools/setup-android.sh: Google's Maven Central mirror first (Central 429s shared cloud IPs).
// Settings repositories only: adding it to project repositories fails the build (FAIL_ON_PROJECT_REPOS).
settingsEvaluated {
    val url = "$MIRROR"
    listOf(pluginManagement.repositories, dependencyResolutionManagement.repositories).forEach { repos ->
        val repo = repos.maven { setUrl(url) }
        repos.remove(repo); repos.addFirst(repo)
    }
}
EOF
then
  echo "  OK    Gradle resolves through Google's Maven Central mirror ($GRADLE_HOME_DIR/init.d/mirror.gradle.kts)"
else
  warn "couldn't write $GRADLE_HOME_DIR/init.d/mirror.gradle.kts: Gradle will ask Maven Central directly"
fi

# ---- 2. Robolectric: the same mirror ----------------------------------------------------------
PROPS="$GRADLE_HOME_DIR/gradle.properties"
if touch "$PROPS" && { if grep -q '^vigilant.mavenMirror=' "$PROPS"; then
       sed -i "s#^vigilant.mavenMirror=.*#vigilant.mavenMirror=$MIRROR#" "$PROPS"
     else echo "vigilant.mavenMirror=$MIRROR" >> "$PROPS"; fi; }; then
  echo "  OK    Robolectric downloads through the mirror too (vigilant.mavenMirror in $PROPS)"
else
  warn "couldn't set vigilant.mavenMirror in $PROPS: Robolectric will ask Maven Central directly"
fi

# ---- 3. Android SDK ----------------------------------------------------------------------------
install_sdk() {
  mkdir -p "$SDK/cmdline-tools" || return 1
  if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    local tmp
    tmp="$(mktemp -d)" || return 1
    if ! { curl -sSfL --retry 2 -o "$tmp/clt.zip" https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip \
        && unzip -qo "$tmp/clt.zip" -d "$tmp" && rm -rf "$SDK/cmdline-tools/latest" \
        && mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"; }; then
      rm -rf "$tmp"; return 1
    fi
    rm -rf "$tmp"
  fi
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1
  if ! "$SDK/cmdline-tools/latest/bin/sdkmanager" "platforms;android-36" "build-tools;35.0.0" "build-tools;36.0.0" \
      "platform-tools" > "$SDK/.setup.log" 2>&1; then
    grep -v '^Picked up JAVA_TOOL_OPTIONS' "$SDK/.setup.log" | tail -5 | sed 's/^/        /'
    return 1
  fi
}
if [ -d "$SDK/platforms/android-36" ] && [ -d "$SDK/build-tools/35.0.0" ] && [ -d "$SDK/build-tools/36.0.0" ]; then
  echo "  OK    Android SDK already at $SDK"
elif install_sdk && [ -d "$SDK/platforms/android-36" ] && [ -d "$SDK/build-tools/35.0.0" ]; then
  echo "  OK    Android SDK installed at $SDK"
else
  warn "Android SDK install failed: does this environment's Network access allow dl.google.com? (BRIEF.md build trap 6)"
fi

# ---- 4. --prewarm: every download a build and a Robolectric test need, on disk now -------------
# A throwaway copy (this repo's own checkout when there is one, else GitHub), so a real checkout is
# never touched; one small Robolectric test (PauseScanningAppTest) compiles every module and pulls
# Robolectric's android-all jar. Gradle's --stop afterwards leaves no daemon behind (the Kotlin
# compile daemon goes with it: checked 2026-09-29; it compiles in 69 s where in-process took 111).
find_checkout() {
  local here="" d
  [ -f "${BASH_SOURCE[0]:-}" ] && here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." 2>/dev/null && pwd)"
  for d in "$here" /home/user/*; do
    if [ -n "$d" ] && [ -f "$d/settings.gradle.kts" ] && grep -q 'rootProject.name = "vigilant"' "$d/settings.gradle.kts"; then
      echo "$d"; return 0
    fi
  done
  return 1
}
prewarm() {
  local budget=$1 warm src rc
  warm="$(mktemp -d)" || return 1
  if src="$(find_checkout)"; then git clone -q --depth 1 "file://$src" "$warm/novig"
  else git clone -q --depth 1 "$REPO_URL" "$warm/novig"; fi || { rm -rf "$warm"; return 1; }
  (cd "$warm/novig" && ANDROID_HOME="$SDK" timeout -k 10 "$budget" ./gradlew --no-daemon --console=plain \
    :app:testDebugUnitTest --tests '*PauseScanningAppTest' > "$warm/prewarm.log" 2>&1)
  rc=$?
  (cd "$warm/novig" && ./gradlew --stop > /dev/null 2>&1)
  pkill -f "$GRADLE_HOME_DIR/caches/.*KotlinCompileDaemon" 2> /dev/null
  [ "$rc" -ne 0 ] && grep -v '^Picked up JAVA_TOOL_OPTIONS' "$warm/prewarm.log" | tail -5 | sed 's/^/        /'
  rm -rf "$warm"
  return "$rc"
}
if [ "$PREWARM" = 1 ]; then
  BUDGET=$(( 250 - ($(date +%s) - SCRIPT_START) ))
  if [ ! -d "$SDK/platforms/android-36" ]; then
    warn "no pre-download without the Android SDK"
  elif [ "$BUDGET" -lt 60 ]; then
    warn "no pre-download: the SDK took most of the 5 minutes a setup script has to be snapshotted"
  else
    START=$(date +%s)
    if prewarm "$BUDGET"; then
      echo "  OK    Gradle and Robolectric downloads are on disk ($(( $(date +%s) - START )) s)"
    else
      warn "pre-download stopped after $(( $(date +%s) - START )) s: a session's first build fetches the rest"
    fi
  fi
fi
echo "  ..    build with ANDROID_HOME=$SDK (e.g. ANDROID_HOME=$SDK ./gradlew :app:testDebugUnitTest)"
exit 0
