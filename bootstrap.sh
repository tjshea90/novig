#!/usr/bin/env bash
# bootstrap.sh — cold start. Verify the checkout, then brief the session.
#
# WHY THIS PRINTS SO LITTLE
# --------------------------
# Everything printed here becomes permanent context that is resent on every
# future turn. So this stays short: where the session stopped, what is next,
# and the standing rules. Narrative detail belongs in CHECKPOINT.md, TASKS.md,
# CLAUDE.md and BRIEF.md, not in growing this file.
#
# NO APP CODE EXISTS YET. This project's first job was the checkpoint system
# itself (tools/, this file, CLAUDE.md, BRIEF.md) — there is no build system,
# no keystore, no Gradle/Kotlin (or other) project scaffold to check yet.
# The checks below are deliberately generic until that scaffold exists; add
# the real ones (toolchain, signing keystore, SDK, build output) the same
# way Portfolio's bootstrap.sh does, the day there is something to check.
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; cd "$D" || exit 1
echo "== novig — bootstrap =="
echo "working dir: $D"; echo

# --- toolchain the checkpoint system itself needs (informational only) ---
command -v git >/dev/null 2>&1 && echo "  OK    git $(git --version 2>&1 | awk '{print $3}')" || echo "  WARN  no git — the whole checkpoint system needs it"
command -v python3 >/dev/null 2>&1 && echo "  OK    python3 $(python3 -V 2>&1 | cut -d' ' -f2) (used by install-hooks.sh, ckpt.sh, capture_inbox.sh)" || echo "  WARN  no python3 — hook JSON emission falls back to plain text"
command -v node >/dev/null 2>&1 && echo "  OK    node $(node -v 2>&1)" || echo "  note  no node yet — fine until a JS test suite exists"

# --- no build system yet: say so plainly rather than checking for things
#     that were never created, which would look like a failed check ---
if [ -f app/build.gradle.kts ] || [ -f build.gradle.kts ] || [ -f package.json ]; then
  echo "  note  a build system now exists — bootstrap.sh has not been updated"
  echo "        to check it yet (toolchain pins, keystore, SDK). Do that next"
  echo "        time real build rules land, the way Portfolio's bootstrap.sh does."
else
  echo "  note  no build system yet (no Gradle/Kotlin, no package.json). Expected"
  echo "        at this stage — the app itself has not been started."
fi

# --- is the safety net actually installed? ---
if bash tools/install-hooks.sh --check >/dev/null 2>&1; then
  echo "  OK    checkpoint hooks are installed at the session root"
else
  echo "  NOTE  checkpoint hooks are not (yet) installed at the session root —"
  echo "        tools/resume.sh and tools/ckpt.sh both repair this automatically."
fi

# --- the checkpoint history ---
# Dirty-tree / mid-change detection is NOT duplicated here — tools/resume.sh
# (the caller) already checks this in detail, with the actual file list, and
# prints it BEFORE calling this script. Saying it twice would just spend
# context repeating one fact; bootstrap.sh only adds what resume.sh doesn't
# already know.
if [ -d .git ]; then
  echo "  OK    checkpoint history present ($(git rev-list --count HEAD 2>/dev/null || echo 0) checkpoints)"
else
  echo "  WARN  no .git — checkpoint history was lost. Run: git init && bash tools/ckpt.sh 'resumed'"
fi
echo
echo "== bootstrap clean =="
echo

if [ -f CHECKPOINT.md ]; then
  echo "##############################################################################"
  echo "#  CHECKPOINT.md — where the last session stopped. START HERE."
  echo "##############################################################################"
  cat CHECKPOINT.md
  echo
fi
if [ -f TASKS.md ]; then
  echo "##############################################################################"
  echo "#  TASKS.md — the scope of the current job. Continue from the first [ ]."
  echo "##############################################################################"
  cat TASKS.md
  echo
fi

echo "##############################################################################"
echo "#  THE RULES THAT MUST NOT BE BROKEN  (full text: BRIEF.md)"
echo "##############################################################################"
cat <<'SHORT'
- Android 16 (API 36) target, optimized for a Moto G 2026 — see BRIEF.md for
  what that constrains once real code exists.
- Purpose: profit using the Novig sportsbook. No strategy, data source or
  architecture decision has been locked in yet — BRIEF.md says so plainly
  rather than inventing rules nobody has actually decided.
- NO signing keystore exists yet. The day one is generated, BRIEF.md's
  "irreplaceable keystore" rule (ported from this account's other Android
  projects) applies immediately and without exception: never regenerate it,
  never change the applicationId, always bump versionCode.
- Checkpoint constantly:  bash tools/ckpt.sh "did" "next"   (fast, no gate)
  Ship at milestones:     bash ship.sh "note"               (full gate)
- Write new requests into TASKS.md, in Tj's own words, before writing any
  code — see CLAUDE.md's "When Tj asks for something new".
SHORT
