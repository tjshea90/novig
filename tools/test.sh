#!/usr/bin/env bash
# tools/test.sh — run Gradle's tests and print a short summary instead of Gradle's whole log.
#
#   bash tools/test.sh                                   the floor: :engine:test :data:test :lab:test :app:testDebugUnitTest
#   bash tools/test.sh :data:test                        one module
#   bash tools/test.sh :data:test --tests '*CnoChecksTest'
#   bash tools/test.sh -Pscreenshots :app:testDebugUnitTest      PNGs of every screen in app/screenshots/
#   VIGILANT_LIVE=1 bash tools/test.sh :data:test --tests '*LiveNovigSmokeTest'
#
# Any Gradle arguments pass through. Prints one line per test task (passed, skipped, failed, or
# "up to date" when Gradle didn't need to rerun it), then only what a failure needs: each failing
# test with the first lines of its message, else the compiler's errors or Gradle's "What went
# wrong". The full log is in /tmp/vigilant-test.log; the exit code is Gradle's. `--continue` is
# always on, so one run reports every module's failures. ANDROID_HOME defaults to
# /opt/android-sdk; with no SDK there, :app's tasks are left out (CI runs them) and it says so.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
LOG=/tmp/vigilant-test.log
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"

ARGS=()
HAS_TASK=0
HAS_FILTER=0
PREV=""
for a in "$@"; do
  case "$PREV" in
    --tests|-x|--exclude-task) ARGS+=("$a"); PREV=""; continue ;;
  esac
  case "$a" in
    --tests) HAS_FILTER=1 ;;
    -*) ;;
    *) HAS_TASK=1 ;;
  esac
  ARGS+=("$a")
  PREV="$a"
done
if [ "$HAS_TASK" = 0 ] && [ "$HAS_FILTER" = 1 ]; then
  echo "  name the test task with --tests, e.g.: bash tools/test.sh :data:test --tests '*CnoChecksTest'"
  exit 2
fi
if [ -d "$ANDROID_HOME/platforms" ]; then
  # The floor is `test`, exactly what CI runs: every module's unit tests (app's are debug only).
  [ "$HAS_TASK" = 0 ] && ARGS=(test "${ARGS[@]}")
else
  [ "$HAS_TASK" = 0 ] && ARGS=(:engine:test :data:test :lab:test :app:testDebugUnitTest "${ARGS[@]}")
  KEPT=()
  DROPPED=0
  for a in "${ARGS[@]}"; do
    case "$a" in
      :app:*) DROPPED=1 ;;
      test) DROPPED=1; KEPT+=(:engine:test :data:test) ;;
      *) KEPT+=("$a") ;;
    esac
  done
  [ "$DROPPED" = 1 ] && echo "  note  no Android SDK at $ANDROID_HOME (bash tools/setup-android.sh): :app's tests left out, CI runs them"
  ARGS=(--configure-on-demand "${KEPT[@]}")
fi

# What this run tests is the code as it is NOW: an edit made while Gradle runs is not covered by the mark below.
SNAP="$( [ -f tools/landing.py ] && python3 -I tools/landing.py tree 2>/dev/null || true)"
START=$(date +%s)
./gradlew "${ARGS[@]}" --continue --console=plain > "$LOG" 2>&1
RC=$?
SECS=$(( $(date +%s) - START ))

python3 tools/gradle_summary.py "$LOG" "$RC" "$SECS"
# A green run lets these modules' code land on main (tools/landing.py; CR, Tj 2026-10-19: "don't ... break anything"). Only what was compiled and tested here counts:
# a run filtered to some tests (--tests) proves nothing about the rest of a module, and the mark is for the tree taken before the run.
if [ "$RC" -eq 0 ] && [ -f tools/landing.py ] && [ "$HAS_FILTER" = 0 ] && [ -n "$SNAP" ]; then
  MODS=""
  for a in "${ARGS[@]}"; do
    case "$a" in
      test|build|check) MODS="all" ;;
      :*:*) m="${a#:}"; m="${m%%:*}"; [ "$MODS" != "all" ] && MODS="$MODS $m" ;;
    esac
  done
  if [ -n "$MODS" ] && python3 -I tools/landing.py mark code $MODS --tree "$SNAP" >/dev/null 2>&1; then
    echo "  land  main may take: ${MODS# }"
    [ -d .git ] && [ -z "$(git status --porcelain --untracked-files=no 2>/dev/null)" ] && PUSH_SYNC_NOW=1 bash tools/push.sh >/dev/null 2>&1
  fi
fi
exit "$RC"
