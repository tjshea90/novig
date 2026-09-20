#!/usr/bin/env bash
# test_resume.sh — prove the checkpoint/handoff system still works, on every
# checkpoint.
#
# WHY THIS EXISTS
# ---------------
# Everything under tools/ exists to survive an interruption, and every
# failure it guards against is SILENT: a briefing that does not parse, a
# hook that was never installed, a "0 commits unpushed" that was really "I
# could not tell". Nothing about the repo looks wrong when one of these
# breaks — you find out when a session is lost, which is exactly too late.
#
# tools/ckpt.sh auto-discovers tools/test_*.sh, so this runs on every single
# checkpoint. That means it must be FAST (well under a second), hermetic (it
# must never touch the real settings file, the real repo state, or the
# network) and quiet unless something fails.
#
# Adapted from Portfolio's tools/test_resume.sh: the JSON-emission,
# hook-merge, checkpoint-numbering, secretscan and unpushed-refuses-to-guess
# checks are the same shape (this account already proved that shape catches
# real bugs). Everything Android/Gradle/keystore-specific is gone — none of
# that exists in this project yet. Also gone: Portfolio's multi-repo hook
# AGGREGATION tests — novig's hooks are deliberately scoped to this repo
# only (see tools/install-hooks.sh's header for why: fantasy-football and
# Portfolio are read-only for this project's work), so there is nothing to
# aggregate and section 4 below instead proves that scoping holds. Added:
# coverage for the raw-INBOX capture path (tools/capture_inbox.sh), ported
# from fantasy-football, which Portfolio does not have at all.
#
#   bash tools/test_resume.sh        # 0 = the handoff is intact
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; cd "$D" || exit 1

# ckpt.sh runs every tools/test_*.sh, and one of these cases runs ckpt.sh
# inside a fixture — without this guard that recurses forever.
[ -n "${RESUME_SELFTEST:-}" ] && exit 0
export RESUME_SELFTEST=1

FAILED=0
ok()   { printf '  ok    %s\n' "$1"; }
bad()  { printf '  FAIL  %s\n' "$1"; FAILED=$((FAILED+1)); }
check(){ if [ "$1" = "0" ]; then ok "$2"; else bad "$2${3:+ — $3}"; fi; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@t GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@t

# ---- 1. everything parses ----------------------------------------------------
# A syntax error in a hook script is invisible: hooks swallow stderr, so the
# safety net just quietly stops existing.
RC=0
for f in tools/*.sh ship.sh bootstrap.sh; do
  [ -f "$f" ] || continue
  bash -n "$f" 2>/dev/null || { echo "      bad syntax: $f"; RC=1; }
done
# Collect via a loop rather than handing bare globs to python3: this
# project has no tools/*.py yet, and an unmatched glob with nullglob off
# passes through as the literal string "tools/*.py" — which then fails to
# open and reads as a syntax error that isn't one.
PYFILES=()
for f in tools/*.py; do [ -f "$f" ] && PYFILES+=("$f"); done
if [ "${#PYFILES[@]}" -gt 0 ]; then
  python3 -c "import ast,sys; [ast.parse(open(f).read()) for f in sys.argv[1:]]" "${PYFILES[@]}" 2>/dev/null || RC=1
fi
python3 -c "import json; json.load(open('tools/session-root-hooks.json'))" 2>/dev/null || RC=1
check "$RC" "every script and the hook template parse"

# ---- 2. a fixture repo, never the real one -----------------------------------
# A throwaway repo that looks like this one. Everything below runs against
# this, never against the real checkout and never against the network: a
# repo with no remote makes `git fetch` fail instantly, whereas the real one
# would sit on a timeout per call whenever GitHub is unreachable — and this
# test runs on EVERY checkpoint, so that would turn a fast checkpoint into a
# slow one at exactly the wrong moment.
FX="$TMP/repo"; mkdir -p "$FX"
cp -r tools "$FX/tools"
rm -f "$FX"/tools/test_*.sh
cp CHECKPOINT.md TASKS.md INBOX.md bootstrap.sh "$FX/" 2>/dev/null || true
( cd "$FX" && git init -q . && git add -A >/dev/null 2>&1 && git commit -qm init >/dev/null 2>&1 )

# ---- 3. the briefing is exactly one JSON object ------------------------------
# Hook stdout is parsed as ONE JSON document.
( cd "$FX" && bash tools/resume.sh ) >"$TMP/brief.json" 2>/dev/null
python3 - "$TMP/brief.json" <<'PY' >/dev/null 2>&1
import json,sys
d=json.load(open(sys.argv[1]))
assert d["hookSpecificOutput"]["hookEventName"]=="SessionStart"
assert d["hookSpecificOutput"]["additionalContext"].strip()
PY
check $? "resume.sh emits one parseable SessionStart object"

( cd "$FX" && bash tools/resume.sh --text ) >"$TMP/brief.txt" 2>/dev/null
if head -c 1 "$TMP/brief.txt" | grep -q '{'; then
  bad "resume.sh --text still wrapped the briefing in JSON"
else
  [ -s "$TMP/brief.txt" ] && ok "resume.sh --text emits plain text" || bad "resume.sh --text emitted nothing"
fi

# ---- 4. the hook installer merges, never clobbers, and NEVER LEAVES THE REPO --
# The naive version of this step is a plain `cp` over the user's own
# settings file. The naive version of the COMMANDS it installs is a glob
# over every repo in the container, which is exactly what this design
# deliberately does NOT do — see tools/install-hooks.sh's header. Both
# properties are checked here.
SET="$TMP/settings.json"
cat > "$SET" <<'JSON'
{"permissions": {"allow": ["Bash(ls:*)"]},
 "env": {"KEEP": "me"},
 "hooks": {"SessionStart": [{"hooks": [{"type": "command", "command": "echo someone-elses-hook"}]}]}}
JSON
( cd "$FX" && CLAUDE_HOOK_SETTINGS="$SET" bash tools/install-hooks.sh --quiet >/dev/null 2>&1 )
python3 - "$SET" "$FX" <<'PY' >/dev/null 2>&1
import json,sys
d=json.load(open(sys.argv[1]))
fx=sys.argv[2]
assert d["permissions"]["allow"] == ["Bash(ls:*)"], "permissions were destroyed"
assert d["env"]["KEEP"] == "me", "env was destroyed"
cmds = [h["command"] for e in d["hooks"]["SessionStart"] for h in e["hooks"]]
assert any("someone-elses-hook" in c for c in cmds), "another project's hook was destroyed"
ours = [c for c in cmds if "novig-checkpoint-hooks" in c]
assert ours, "our hook was not installed"
assert all("__NOVIG_TOOLS__" not in c for c in ours), "placeholder was left unsubstituted"
assert all(fx in c for c in ours), "our hook does not point at this repo's own tools/"
assert all("*/" not in c and "for f in" not in c for c in ours), \
    "our hook globs over sibling directories — it must touch only this repo"
PY
check $? "install-hooks merges (permissions/env/foreign hooks survive) and installs an absolute, repo-scoped path only"

# All FIVE events end up scoped the same way, not just SessionStart.
python3 - "$SET" "$FX" <<'PY' >/dev/null 2>&1
import json,sys
d=json.load(open(sys.argv[1])); fx=sys.argv[2]
for event in ("SessionStart","UserPromptSubmit","PostToolUse","Stop","PreCompact"):
    entries = d["hooks"].get(event, [])
    cmds = [h["command"] for e in entries for h in e.get("hooks", [])]
    ours = [c for c in cmds if "novig-checkpoint-hooks" in c]
    assert ours, "no novig hook installed for %s" % event
    assert all(fx in c and "*/" not in c for c in ours), "%s hook is not repo-scoped" % event
PY
check $? "every hook event (SessionStart/UserPromptSubmit/PostToolUse/Stop/PreCompact) is installed, repo-scoped"

# A legacy/foreign entry carrying OUR marker from a stale prior install must
# be REPLACED, not kept alongside the new one — never two copies of the same
# hook firing for the same event.
cat > "$SET" <<'JSON'
{"hooks": {"SessionStart": [{"hooks": [{"type": "command", "command": "echo stale  # novig-checkpoint-hooks"}]}]}}
JSON
( cd "$FX" && CLAUDE_HOOK_SETTINGS="$SET" bash tools/install-hooks.sh --quiet >/dev/null 2>&1 )
python3 - "$SET" <<'LEGACY' >/dev/null 2>&1
import json,sys
d=json.load(open(sys.argv[1]))
cmds=[h["command"] for x in d["hooks"]["SessionStart"] for h in x["hooks"]]
assert len(cmds)==1, "stale marked entry kept alongside the new one: %r" % cmds
assert "resume.sh" in cmds[0]
LEGACY
check $? "a stale marked entry is replaced, not duplicated"

BEFORE="$(cat "$SET")"
( cd "$FX" && CLAUDE_HOOK_SETTINGS="$SET" bash tools/install-hooks.sh --quiet >/dev/null 2>&1 )
[ "$BEFORE" = "$(cat "$SET")" ] && ok "install-hooks is idempotent" || bad "install-hooks rewrote an already-current file"
( cd "$FX" && CLAUDE_HOOK_SETTINGS="$SET" bash tools/install-hooks.sh --check >/dev/null 2>&1 )
check $? "install-hooks --check reports 'current' once installed"

# A SIBLING repo's own, differently-scoped hooks in the same settings file
# must be left completely alone by an install run from THIS repo — this is
# the direct test of "never touches another repo's entries".
cat > "$SET" <<'JSON'
{"hooks": {"SessionStart": [{"hooks": [{"type": "command", "command": "bash /home/user/Portfolio/tools/resume.sh  # portfolio-checkpoint-hooks"}]}]}}
JSON
( cd "$FX" && CLAUDE_HOOK_SETTINGS="$SET" bash tools/install-hooks.sh --quiet >/dev/null 2>&1 )
python3 - "$SET" <<'SIB' >/dev/null 2>&1
import json,sys
d=json.load(open(sys.argv[1]))
cmds=[h["command"] for x in d["hooks"]["SessionStart"] for h in x["hooks"]]
assert any("Portfolio" in c for c in cmds), "a sibling repo's own hook entry was removed"
assert any("novig-checkpoint-hooks" in c for c in cmds), "our own hook was not added alongside it"
assert len(cmds) == 2, "expected exactly the sibling's entry plus ours: %r" % cmds
SIB
check $? "a sibling repo's own hook entry is left untouched, ours added alongside it"

# ---- 5. checkpoint numbers only ever go up -----------------------------------
# The shallow-clone regression: a fresh container has fewer commits than the
# history it was cloned from, so a commit-count-based number could walk
# BACKWARDS.
echo '# CHECKPOINT 9000 — read me first, then TASKS.md' > "$FX/CHECKPOINT.md"
( cd "$FX" && CLAUDE_HOOK_SETTINGS="$TMP/fx-settings.json" \
    bash tools/ckpt.sh "self-test" "self-test" >/dev/null 2>&1 )
GOT="$(sed -n '1s/^# CHECKPOINT \([0-9][0-9]*\).*/\1/p' "$FX/CHECKPOINT.md" 2>/dev/null || echo 0)"
if [ "${GOT:-0}" -gt 9000 ]; then
  ok "checkpoint numbers are monotonic in a shallow clone (9000 -> $GOT)"
else
  bad "checkpoint number went backwards" "9000 -> ${GOT:-none}"
fi
# THE "SESSION FORGOT THE FIRST ACTION" PATH.
# ckpt.sh must put the hooks back by itself, or a session that never read
# CLAUDE.md's install step runs to its usage cap with nothing auto-saved.
# The fixture ckpt.sh above pointed at $TMP/fx-settings.json, which did not
# exist before it ran.
if [ -f "$TMP/fx-settings.json" ] && grep -q 'novig-checkpoint-hooks' "$TMP/fx-settings.json" 2>/dev/null; then
  ok "ckpt.sh reinstalls missing hooks by itself"
else
  bad "ckpt.sh did NOT repair missing hooks — a session that skips the install step stays unprotected"
fi

grep -q '^\*\*Branch:\*\*' "$FX/CHECKPOINT.md" && ok "CHECKPOINT.md records its branch" \
  || bad "CHECKPOINT.md does not say which branch the work is on"

# ---- 6. the raw INBOX capture path --------------------------------------------
# This is the layer Portfolio does not have at all (ported from
# fantasy-football instead) — a UserPromptSubmit hook that must get the
# message onto disk before any tool call, and must NEVER block the prompt
# no matter what goes wrong.
IB="$TMP/inboxfx"; mkdir -p "$IB"
cp -r tools "$IB/tools"; rm -f "$IB"/tools/test_*.sh
printf '# INBOX\n' > "$IB/INBOX.md"
( cd "$IB" && git init -q . && git add -A >/dev/null 2>&1 && git commit -qm init >/dev/null 2>&1 )

echo '{"prompt": "capture-me: does this reach disk before any tool call?"}' | \
  ( cd "$IB" && bash tools/capture_inbox.sh >/dev/null 2>&1 )
if grep -q 'capture-me: does this reach disk before any tool call?' "$IB/INBOX.md" 2>/dev/null; then
  ok "capture_inbox.sh writes the raw prompt to INBOX.md"
else
  bad "capture_inbox.sh did not write the prompt to INBOX.md"
fi
( cd "$IB" && git log --oneline -1 2>/dev/null | grep -q 'auto-checkpoint:' ) \
  && ok "capture_inbox.sh commits the captured message immediately" \
  || bad "capture_inbox.sh wrote INBOX.md but did not commit it — a cap right after would lose it"

# Malformed / empty stdin must never crash the hook or block the prompt —
# CLAUDE.md is explicit that this can never be allowed to block.
printf '' | ( cd "$IB" && bash tools/capture_inbox.sh >/dev/null 2>&1 )
check $? "capture_inbox.sh exits 0 on empty stdin (never blocks the prompt)"
echo 'not json at all' | ( cd "$IB" && bash tools/capture_inbox.sh >/dev/null 2>&1 )
check $? "capture_inbox.sh exits 0 on malformed JSON (never blocks the prompt)"

# The installed hook set must actually wire UserPromptSubmit to
# capture_inbox.sh — this is the one event Portfolio's own template doesn't
# define at all, so it's worth checking explicitly rather than assuming the
# generic install test above covered it.
( cd "$IB" && CLAUDE_HOOK_SETTINGS="$TMP/ib-settings.json" bash tools/install-hooks.sh --quiet >/dev/null 2>&1 )
python3 - "$TMP/ib-settings.json" <<'PY' >/dev/null 2>&1
import json,sys
d=json.load(open(sys.argv[1]))
cmds=[h["command"] for e in d["hooks"]["UserPromptSubmit"] for h in e["hooks"]]
assert any("capture_inbox.sh" in c for c in cmds)
PY
check $? "install-hooks wires UserPromptSubmit to capture_inbox.sh"

# ---- 7. the secret scan still has teeth --------------------------------------
S="$TMP/secret"; mkdir -p "$S"; cp -r tools "$S/tools"
( cd "$S" && git init -q . && git add -A >/dev/null 2>&1 && git commit -qm init >/dev/null 2>&1 )
printf 'token = "ghp_%s"\n' "0123456789abcdef0123456789abcdef01" > "$S/leak.txt"
( cd "$S" && git add -A >/dev/null 2>&1 )
( cd "$S" && bash tools/secretscan.sh >/dev/null 2>&1 ) && bad "secretscan missed a GitHub token" || ok "secretscan still catches a live-shaped token"
rm -f "$S/leak.txt"
printf 'EXAMPLE_KEY = "ghp_%s"  # PLACEHOLDER\n' "0123456789abcdef0123456789abcdef01" > "$S/fixture.txt"
( cd "$S" && git add -A >/dev/null 2>&1 )
( cd "$S" && bash tools/secretscan.sh >/dev/null 2>&1 ) && ok "secretscan ignores an obvious placeholder" \
  || bad "secretscan false-positives on a placeholder — this wedges ALL auto-saving"

# ---- 8. 'nothing to push' is never guessed -----------------------------------
# Answering 0 when the truth is unknown is how work gets left in a dead
# container while every status line says it was saved.
( cd "$S" && bash tools/unpushed.sh >/dev/null 2>&1 ) \
  && bad "unpushed.sh claimed a count with no remote at all" \
  || ok "unpushed.sh refuses to guess when there is no remote"

# ---- 9. ship.sh refuses honestly when there is nothing to ship ----------------
# A milestone gate that silently no-ops or crashes on a project with no
# build system yet would be worse than one that just says so.
NS="$TMP/noship"; mkdir -p "$NS/tools"; cp -r tools/. "$NS/tools/"; rm -f "$NS"/tools/test_*.sh
cp ship.sh bootstrap.sh CHECKPOINT.md TASKS.md INBOX.md "$NS/" 2>/dev/null
( cd "$NS" && git init -q . && git add -A >/dev/null 2>&1 && git commit -qm init >/dev/null 2>&1 )
OUT="$( cd "$NS" && bash ship.sh "test note" 2>&1 )"; RC=$?
if [ "$RC" -ne 0 ] && printf '%s' "$OUT" | grep -qi 'nothing to build or release'; then
  ok "ship.sh refuses clearly when no build system exists, instead of faking success"
else
  bad "ship.sh did not refuse cleanly with no build system present" "exit=$RC"
fi

echo
if [ "$FAILED" -eq 0 ]; then
  echo "  checkpoint system: all checks green"
  exit 0
fi
echo "  checkpoint system: $FAILED CHECK(S) FAILED — the handoff is not safe"
exit 1
