#!/usr/bin/env bash
# Makes a fresh Claude Code on the web container able to build and test Vigilant, WITHOUT touching
# Maven Central (BRIEF.md build trap 6). Idempotent: safe to run every session, and self-contained
# (no path into this repo), so the same file can be pasted as the cloud environment's setup script.
#
# Why Maven Central is avoided (researched 2026-09-27, BRIEF.md trap 6): Central rate-limits by
# EGRESS IP, on the aggregate traffic it sees from that IP. Every cloud session leaves through a
# small pool of shared egress addresses and starts with an empty ~/.gradle and ~/.m2 (a full
# Vigilant build pulls ~1 GB, thousands of requests), so the pool keeps crossing Central's
# threshold and gets 429 "Too Many Requests" for everyone on it, for minutes to hours (blocks
# escalate on repeat offenders). Nothing one account does changes that IP's total, and retrying
# makes it worse. Google's public mirror of Maven Central (Cloud Storage, not rate-limited this
# way) serves the same artifacts, so:
#   1. Gradle: an init script puts the mirror FIRST for plugins and dependencies (Gradle stops at
#      the first repository that errors, so a 429 never falls through to the next one).
#   2. Robolectric: it downloads its android-all jars itself at test time, outside Gradle (the
#      mirror in 1 doesn't cover it). `vigilant.mavenMirror` in ~/.gradle/gradle.properties makes
#      app/build.gradle.kts hand the mirror to Robolectric; CI never sets it and keeps Central.
#   3. The Android SDK (platform 36, build-tools 36) from dl.google.com, into /opt/android-sdk.
set -euo pipefail

MIRROR="https://maven-central.storage-download.googleapis.com/maven2/"
SDK="${ANDROID_HOME:-/opt/android-sdk}"
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}"

# ---- 1. Gradle: Google's Maven Central mirror first ------------------------------------------
mkdir -p "$GRADLE_HOME_DIR/init.d"
cat > "$GRADLE_HOME_DIR/init.d/mirror.gradle.kts" <<EOF
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
echo "  OK    Gradle resolves through Google's Maven Central mirror ($GRADLE_HOME_DIR/init.d/mirror.gradle.kts)"

# ---- 2. Robolectric: the same mirror ----------------------------------------------------------
PROPS="$GRADLE_HOME_DIR/gradle.properties"
touch "$PROPS"
if grep -q '^vigilant.mavenMirror=' "$PROPS"; then
  sed -i "s#^vigilant.mavenMirror=.*#vigilant.mavenMirror=$MIRROR#" "$PROPS"
else
  echo "vigilant.mavenMirror=$MIRROR" >> "$PROPS"
fi
echo "  OK    Robolectric downloads through the mirror too (vigilant.mavenMirror in $PROPS)"

# ---- 3. Android SDK ----------------------------------------------------------------------------
if [ -d "$SDK/platforms/android-36" ] && [ -d "$SDK/build-tools/36.0.0" ]; then
  echo "  OK    Android SDK already at $SDK"
else
  mkdir -p "$SDK/cmdline-tools"
  if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    TMP="$(mktemp -d)"
    curl -sSfL -o "$TMP/clt.zip" https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
    unzip -qo "$TMP/clt.zip" -d "$TMP"
    rm -rf "$SDK/cmdline-tools/latest"
    mv "$TMP/cmdline-tools" "$SDK/cmdline-tools/latest"
    rm -rf "$TMP"
  fi
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true
  "$SDK/cmdline-tools/latest/bin/sdkmanager" "platforms;android-36" "build-tools;36.0.0" "platform-tools" > /dev/null
  echo "  OK    Android SDK installed at $SDK"
fi
echo "  ..    build with ANDROID_HOME=$SDK (e.g. ANDROID_HOME=$SDK ./gradlew :app:testDebugUnitTest)"
