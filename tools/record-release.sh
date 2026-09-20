#!/usr/bin/env bash
# record-release.sh — write a shipped version into BUILDLOG.md, AFTER GitHub
# built it.
#
# WHY THIS IS A SEPARATE STEP FROM ship.sh
# ------------------------------------------
# BUILDLOG.md is load-bearing, not documentation: once ship.sh's real gate
# exists, it will read this file to decide whether the next build could
# actually install over what's already out there (Android refuses to
# install a versionCode that isn't strictly higher). A line in it is a claim
# that a release EXISTS.
#
# ship.sh only gates and triggers — it does not build the APK, GitHub does —
# so at the moment ship.sh finishes, the release does not exist yet. Writing
# the line there would make BUILDLOG lie whenever a run failed, and the next
# release would then be gated against a version nobody can actually install.
# So: ship.sh pushes and triggers, GitHub builds, and only once that run is
# GREEN does this record it. This is the same split Portfolio's
# ship.sh/record-release.sh use, ported here unmodified in spirit.
#
# WHY THIS TAKES versionCode AS AN ARGUMENT, UNLIKE PORTFOLIO'S
# -----------------------------------------------------------------
# Portfolio's record-release.sh scrapes versionCode straight out of
# app/build.gradle.kts, because that file is guaranteed to exist there. This
# project's toolchain is still TBD (see BRIEF.md) — there may not even BE a
# Gradle file — so this takes the version code explicitly instead of
# guessing a file to grep. The day the toolchain is decided, either keep it
# explicit (simplest) or add the same scrape Portfolio uses.
#
#   bash tools/record-release.sh v1.0 1 "what changed"
#
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$D" || exit 1

TAG="${1:-}"; VCODE="${2:-}"; NOTE="${3:-}"
if [ -z "$TAG" ] || [ -z "$VCODE" ] || [ -z "$NOTE" ]; then
  echo "usage: bash tools/record-release.sh v1.0 1 \"what changed this release\""
  exit 1
fi
case "$VCODE" in
  ''|*[!0-9]*) echo "FAIL: versionCode must be a plain integer, got '$VCODE'"; exit 1 ;;
esac

VNAME="${TAG#v}"

if grep -q "^| v${VNAME} |" BUILDLOG.md 2>/dev/null; then
  echo "  ..    v$VNAME is already in BUILDLOG.md — nothing to record"
  exit 0
fi

printf '| v%s | code %s | %s | %s\n' \
  "$VNAME" "$VCODE" "$(date -u +%Y-%m-%dT%H:%MZ)" "$NOTE" >> BUILDLOG.md

git add BUILDLOG.md >/dev/null 2>&1
if ! bash tools/secretscan.sh; then
  git reset -q >/dev/null 2>&1
  echo "  FAIL  a credential is in the tree — nothing recorded."
  exit 1
fi
git commit -q -m "record v$VNAME (code $VCODE): $NOTE

Built and signed by GitHub Actions; this line is the durable record that the
release exists. See the run and its APK under Releases.

Co-Authored-By: Claude <noreply@anthropic.com>" >/dev/null 2>&1

if bash tools/push.sh; then
  echo "  OK    recorded v$VNAME (code $VCODE) in BUILDLOG.md and pushed"
else
  echo "  WARN  recorded locally but COULD NOT PUSH. Retry: git push origin HEAD"
  exit 1
fi
