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

# ---- full suite of what this container can actually verify --------------------
if [ ! -f app/build.gradle.kts ]; then
  echo "  FAIL  no app/build.gradle.kts — nothing to build or release."
  exit 1
fi

echo "  ..    running engine+data's full test suite (app module needs CI — see header)"
if ! ./gradlew --configure-on-demand :engine:test :data:test --console=plain > /tmp/ship-gradle-test.log 2>&1; then
  echo "  FAIL  engine/data tests are red. Log:"
  tail -40 /tmp/ship-gradle-test.log
  exit 1
fi
echo "  OK    engine+data tests green"

# ---- versionCode must be strictly higher than every code already shipped ------
VERSION_NAME="$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | head -1 | sed -E 's/versionName = "([^"]+)"/\1/')"
VERSION_CODE="$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | head -1 | sed -E 's/versionCode = ([0-9]+)/\1/')"
if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
  echo "  FAIL  could not read versionName/versionCode from app/build.gradle.kts"
  exit 1
fi

HIGHEST_SHIPPED=0
if [ -f BUILDLOG.md ]; then
  HIGHEST_SHIPPED="$(grep -oE '\| code [0-9]+' BUILDLOG.md | grep -oE '[0-9]+' | sort -n | tail -1)"
  [ -z "$HIGHEST_SHIPPED" ] && HIGHEST_SHIPPED=0
fi
if [ "$VERSION_CODE" -le "$HIGHEST_SHIPPED" ]; then
  echo "  FAIL  versionCode $VERSION_CODE is not higher than the highest shipped"
  echo "        code in BUILDLOG.md ($HIGHEST_SHIPPED). Android refuses to install"
  echo "        a build whose versionCode doesn't strictly increase — bump it in"
  echo "        app/build.gradle.kts before shipping."
  exit 1
fi
echo "  OK    versionCode $VERSION_CODE > highest shipped ($HIGHEST_SHIPPED); versionName $VERSION_NAME"

# ---- push (never a tag — the release workflow creates it server-side) ---------
if bash tools/push.sh; then
  echo "  OK    pushed to GitHub"
else
  echo "  FAIL  push failed — see tools/push.sh output above"
  exit 1
fi

bash tools/ckpt.sh "pre-release: $NOTE (versionCode $VERSION_CODE, v$VERSION_NAME)" \
  "Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v$VERSION_NAME), then run: bash tools/record-release.sh v$VERSION_NAME $VERSION_CODE \"$NOTE\"" \
  >/dev/null 2>&1

echo ""
echo "  ==    gated and pushed. Next: trigger release.yml, confirm green, then"
echo "        tools/record-release.sh v$VERSION_NAME $VERSION_CODE \"$NOTE\""
