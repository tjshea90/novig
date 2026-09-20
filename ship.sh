#!/usr/bin/env bash
# ship.sh — cut a release. GITHUB BUILDS AND SIGNS THE APK (release.yml);
# this gates, bumps nothing itself, and pushes. See CLAUDE.md's "Releasing"
# section for the full plan, ported from Portfolio's ship.sh/android.yml.
#
#   bash ship.sh "what changed this release"
#
# WHAT THIS DOES NOT DO, ON PURPOSE
# ------------------------------------
# This dev container has no Android SDK (BRIEF.md's Toolchain section) — so
# unlike Portfolio's ship.sh, this cannot run the Android `app` module's own
# tests/build locally as part of the gate. It runs the full suite of what
# CAN be verified here (`engine`+`data`, plain-Kotlin, real unit tests), and
# leans on `.github/workflows/ci.yml` — which DOES have a real SDK — having
# already been confirmed green on the commit being shipped. This script does
# not itself trigger or check that CI run; whoever runs `ship.sh` is
# responsible for having confirmed it first (a Claude session doing so
# checks via the GitHub MCP tools before calling this).
#
# This also does not trigger the release workflow or write BUILDLOG.md —
# per tools/record-release.sh's own header, that only happens after a real
# GitHub Actions run is confirmed green, which needs the MCP GitHub tools
# this plain bash script doesn't have. ship.sh's job ends at "gated, bumped
# nothing itself, pushed" — the calling session triggers
# `.github/workflows/release.yml` (mcp__github__actions_run_trigger),
# confirms green (mcp__github__get_release_by_tag), then runs
# tools/record-release.sh.
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; cd "$D" || exit 1

NOTE="${1:-}"; [ -z "$NOTE" ] && { echo "FAIL: a one-line change note is required."; exit 1; }

# COMMIT BEFORE GATING. A gate run against a dirty tree proves nothing about
# what is actually committed, and leaves the tree looking like the last
# session was interrupted when it wasn't (tools/resume.sh's mid-change
# warning must not cry wolf on every ship attempt).
if [ -d .git ] && [ -n "$(git status --porcelain 2>/dev/null)" ]; then
  bash tools/ckpt.sh "pre-ship: $NOTE" "ship.sh gates and releases this" >/dev/null 2>&1
  echo "  OK    committed the working tree before gating"
fi

echo "== ship gates =="

# ---- whatever fast checks exist, same discovery ckpt.sh uses -----------------
PASS=0; FAIL=0; REDS=""; RAN_ANY=0
if [ -f package.json ] && command -v npm >/dev/null 2>&1 && command -v node >/dev/null 2>&1 \
   && node -e "const p=require('./package.json'); process.exit(p.scripts && p.scripts.test ? 0 : 1)" 2>/dev/null; then
  RAN_ANY=1
  if npm test --silent >/dev/null 2>&1; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); REDS="$REDS npm-test"; fi
fi
for T in tools/test_*.js tools/test_*.sh tools/test_*.py; do
  [ -f "$T" ] || continue
  RAN_ANY=1
  case "$T" in *.sh) RUNNER="bash" ;; *.py) RUNNER="python3" ;; *) RUNNER="node" ;; esac
  if "$RUNNER" "$T" >/dev/null 2>&1; then PASS=$((PASS+1)); else FAIL=$((FAIL+1)); REDS="$REDS $(basename "$T")"; fi
done
if [ "$RAN_ANY" -gt 0 ]; then
  if [ "$FAIL" -gt 0 ]; then
    echo "  FAIL  $FAIL fast check(s) red:$REDS — not shipping this."
    exit 1
  fi
  echo "  OK    all $PASS fast checks green"
else
  echo "  note  no fast checks exist yet"
fi

# ---- is there anything to actually build and release? -------------------------
if [ -f app/build.gradle.kts ] || [ -f build.gradle.kts ] || [ -f package.json ] && [ -d app ]; then
  echo "  FAIL  a build system now exists, but ship.sh has not been updated to gate"
  echo "        it yet (full suite, versionCode check, GitHub Actions trigger)."
  echo "        Write that gate now — see this file's own header for the shape to"
  echo "        follow (Portfolio's ship.sh) — then re-run."
  exit 1
fi

echo "  FAIL  no Android project scaffold exists yet (no app/build.gradle.kts,"
echo "        no build.gradle.kts) — there is nothing to build or release."
echo "        Stand up the project first (see BRIEF.md's open TBDs and"
echo "        CLAUDE.md's \"Releasing\" section for the order to do it in)."
echo "        The checkpoint itself is still safe: 'bash tools/ckpt.sh \"$NOTE\" \"next\"'"
echo "        records this work without claiming a release that doesn't exist."
exit 1
