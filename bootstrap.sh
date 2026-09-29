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
# The build checks stay to what a session needs before its first build: the
# Android SDK (BRIEF.md build trap 6) and the committed signing keystore.
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; cd "$D" || exit 1
echo "== novig — bootstrap =="
echo "working dir: $D"; echo

# --- toolchain the checkpoint system itself needs (informational only) ---
command -v git >/dev/null 2>&1 && echo "  OK    git $(git --version 2>&1 | awk '{print $3}')" || echo "  WARN  no git — the whole checkpoint system needs it"
command -v python3 >/dev/null 2>&1 && echo "  OK    python3 $(python3 -V 2>&1 | cut -d' ' -f2) (used by install-hooks.sh, ckpt.sh, capture_inbox.sh)" || echo "  WARN  no python3 — hook JSON emission falls back to plain text"
command -v node >/dev/null 2>&1 && echo "  OK    node $(node -v 2>&1)" || echo "  note  no node yet — fine until a JS test suite exists"

# --- what the first build needs ---
if [ -f app/build.gradle.kts ]; then
  SDK="${ANDROID_HOME:-/opt/android-sdk}"
  if [ -d "$SDK/platforms" ]; then
    echo "  OK    Android SDK at $SDK (build with ANDROID_HOME=$SDK)"
  else
    echo "  WARN  no Android SDK at $SDK: bash tools/setup-android.sh (BRIEF.md build trap 6)"
  fi
  if [ -f app/keystore/vigilant-debug.jks ]; then
    echo "  OK    signing keystore present (permanent: BRIEF.md)"
  else
    echo "  WARN  app/keystore/vigilant-debug.jks is missing: restore it from git, never regenerate it"
  fi
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
if git rev-parse --git-dir >/dev/null 2>&1; then
  echo "  OK    checkpoint history present ($(git rev-list --count HEAD 2>/dev/null || echo 0) commits)"
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
  echo "#  TASKS.md — open items only. Continue from the first [ ] of the current job."
  echo "##############################################################################"
  # Open boxes only, one line each, never the whole file: this briefing has to
  # fit Claude Code's 10,000-character hook cap (tools/resume.sh), and
  # TASKS.md passed 200 KB on 2026-09-28. Each line carries its TASKS.md line
  # number, so the full text is one read away.
  python3 - <<'PY' 2>/dev/null || grep -n '^- \[ \]' TASKS.md | cut -c1-140 | tail -20
import re
lines = open("TASKS.md", encoding="utf-8", errors="replace").read().split("\n")
heads = [i for i, l in enumerate(lines) if l.startswith("## ")]
last = heads[-1] if heads else -1
boxes = [i for i, l in enumerate(lines) if re.match(r"\s*- \[ \]", l)]
def short(s, n):
    s = s.strip()
    return s if len(s) <= n else s[: n - 1] + "…"
cur = [i for i in boxes if i > last]
old = [i for i in boxes if i < last]
print(f"{len(boxes)} open item(s). Lnnn = its line in TASKS.md; read there for the full text.")
if last >= 0:
    print(f"\nCURRENT JOB (last section, L{last + 1}): {short(lines[last][3:], 260)}")
    for i in cur:
        print(f"  L{i + 1:<5} {short(lines[i], 140)}")
    if not cur:
        print("  (no open boxes: this job is finished)")
if old:
    print(f"\nOLDER OPEN ITEMS ({len(old)}; some may be stale, check before acting):")
    for i in old[-12:]:
        print(f"  L{i + 1:<5} {short(lines[i], 110)}")
    if len(old) > 12:
        print(f"  ... {len(old) - 12} earlier: grep -n '^- \\[ \\]' TASKS.md")
PY
  echo
fi

echo "##############################################################################"
echo "#  THE RULES THAT MUST NOT BE BROKEN  (full text: BRIEF.md)"
echo "##############################################################################"
cat <<'SHORT'
- Android 16 (API 36) target, optimized for a Moto G 2026.
- Purpose: profit using the Novig sportsbook. What's decided (fair odds,
  taker price, fees, when the app reads odds) is in BRIEF.md's "Locked
  architecture decisions".
- The signing keystore is committed and permanent (BRIEF.md): never
  regenerate it, never change the applicationId, always bump versionCode.
- Checkpoint constantly:  bash tools/ckpt.sh "did" "next"   (fast, no gate)
  Ship at milestones:     bash ship.sh "note"               (full gate)
- Write new requests into TASKS.md, in Tj's own words, before writing any
  code — see CLAUDE.md's "When Tj asks for something new".
- Skills (.claude/skills/): load the matching one before the work — compose-
  state-and-effects, compose-performance, kotlin-concurrency-and-flow,
  compose-ui-testing-patterns; test-protocols for light/full tests (CLAUDE.md
  "Skills for this app").
SHORT
