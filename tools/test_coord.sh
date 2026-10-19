#!/usr/bin/env bash
# test_coord.sh - proves tools/coord.py (no lease: sessions, claims, the board, CR 2026-10-19) and tools/push.sh against a local bare repository standing in for GitHub.
# Two sessions (A, B) share it.   bash tools/test_coord.sh   -> one line per check and "PASS n checks"; exit 1 on a failure.
set -uo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
FAILS=0; N=0
ok()  { N=$((N+1)); echo "  ok    $1"; }
bad() { N=$((N+1)); FAILS=$((FAILS+1)); echo "  FAIL  $1"; }
expect() { # expect "name" wanted_rc actual_rc
  if [ "$3" -eq "$2" ]; then ok "$1"; else bad "$1 (wanted exit $2, got $3)"; fi; }

git init -q --bare "$T/origin.git"
git init -q "$T/work"; git -C "$T/work" remote add origin "$T/origin.git"
git -C "$T/work" -c user.name=t -c user.email=t@t commit -q --allow-empty -m init
git -C "$T/work" push -q origin HEAD:refs/heads/main 2>/dev/null

A() { COORD_REPO="$T/work" COORD_HOME="$T/a" COORD_LABEL=acctA COORD_RENEW_S=0 COORD_WAIT_POLL_S=1 python3 -I "$D/tools/coord.py" "$@"; }
B() { COORD_REPO="$T/work" COORD_HOME="$T/b" COORD_LABEL=acctB COORD_RENEW_S=0 COORD_WAIT_POLL_S=1 python3 -I "$D/tools/coord.py" "$@"; }

# 1. no lease: both sessions check in, neither is refused, and each sees the other
A acquire --kind interactive --task "chat rebuild" >/dev/null 2>&1; expect "A checks in" 0 $?
git -C "$T/origin.git" rev-parse -q --verify refs/heads/claude-coord >/dev/null && ok "the claude-coord branch exists on the remote" || bad "no claude-coord branch"
B acquire --kind routine --task "rotation" >/dev/null 2>&1; expect "B checks in too (nobody holds the repo any more)" 0 $?
A check >/dev/null 2>&1; expect "A's push check says go" 0 $?
B check >/dev/null 2>&1; expect "B's push check says go" 0 $?
A sessions 2>/dev/null | grep -q "acctB" && ok "A sees B working" || bad "A does not see B"
OUT="$(A status 2>/dev/null)"
case "$OUT" in *"WORKING acctB"*) ok "the briefing lists the other live session";; *) bad "no WORKING line: $OUT";; esac
case "$OUT" in *"HOLDS THIS REPO"*|*"lease is"*|*"Do not edit"*) bad "the briefing still talks of a lease: $OUT";; *) ok "the briefing has no lease text";; esac
# 2. claims: one job, one session
A claim CR --note "chat rebuild" >/dev/null 2>&1; expect "A claims CR" 0 $?
B claim cr >/dev/null 2>&1; expect "B cannot claim CR while A is on it (ids are case-blind)" 3 $?
A claim CR >/dev/null 2>&1; expect "claiming your own item again is fine" 0 $?
B claim CH21 >/dev/null 2>&1; expect "B claims another item" 0 $?
A claims 2>/dev/null | grep -q "CH21" && ok "claims are listed" || bad "CH21 not listed"
(B status 2>/dev/null | grep -q "CLAIMS.*CR") && ok "the briefing lists claims" || bad "no CLAIMS line"
# 3. a lapsed claim (its session went quiet) is taken over
python3 - "$T" <<'PY'
import json,subprocess,sys,time,glob,os
T=sys.argv[1]
subprocess.run(["git","clone","-q","--branch","claude-coord",T+"/origin.git",T+"/edit"],check=True)
for p in glob.glob(T+"/edit/claims/CR.json")+glob.glob(T+"/edit/sessions/acctA-*.json"):
    d=json.load(open(p)); d["beat"]=int(time.time())-7200; json.dump(d,open(p,"w"))
subprocess.run(["git","-C",T+"/edit","-c","user.name=t","-c","user.email=t@t","commit","-qam","age"],check=True)
subprocess.run(["git","-C",T+"/edit","push","-q","origin","HEAD:refs/heads/claude-coord"],check=True)
PY
B claim CR >/dev/null 2>&1; expect "B takes over A's lapsed claim" 0 $?
B unclaim CR >/dev/null 2>&1; expect "B gives it back" 0 $?
A claim CR >/dev/null 2>&1; expect "A can take it again" 0 $?
# 4. RELEASE: one release at a time; --wait gives up after its time, then works once it is free
A claim RELEASE --note "v1.0.6" >/dev/null 2>&1; expect "A claims RELEASE" 0 $?
B claim RELEASE --wait 0.05 >/dev/null 2>&1; expect "B waits for RELEASE and gives up after its time" 3 $?
A unclaim RELEASE >/dev/null 2>&1
B claim RELEASE --wait 0.05 >/dev/null 2>&1; expect "B gets RELEASE once A gave it back" 0 $?
# 5. check out: the session file and its claims go
B release >/dev/null 2>&1; expect "B checks out" 0 $?
A claims 2>/dev/null | grep -q "CH21" && bad "B's claims survived its check-out" || ok "B's claims went with it"
# 6. notes pass between sessions
A post "hello from A" "line of body" >/dev/null 2>&1; expect "A posts a note" 0 $?
B board 8 2>/dev/null | grep -q "hello from A" && ok "B reads A's note" || bad "B did not see the note"
# 7. EVERY failure lets work through
git init -q "$T/lonely"; git -C "$T/lonely" remote add origin "$T/does-not-exist.git"
COORD_REPO="$T/lonely" COORD_HOME="$T/c" COORD_LABEL=acctC python3 -I "$D/tools/coord.py" check >/dev/null 2>&1; expect "an unreachable remote does not hold a push back" 0 $?
COORD_OFF=1 B check >/dev/null 2>&1; expect "COORD_OFF lets everything through" 0 $?
A wait-free >/dev/null 2>&1; expect "wait-free (old routine prompts) returns at once" 0 $?
# 8. the dead-man STOP still stops every session (deadman/STOP.json on cloud-config)
git clone -q "$T/origin.git" "$T/cc" 2>/dev/null; git -C "$T/cc" checkout -q --orphan cloud-config; mkdir -p "$T/cc/deadman"; echo '{"at":1}' > "$T/cc/deadman/STOP.json"
git -C "$T/cc" add -A; git -C "$T/cc" -c user.name=t -c user.email=t@t commit -q -m stop; git -C "$T/cc" push -q origin HEAD:refs/heads/cloud-config
A acquire >/dev/null 2>&1; expect "the dead-man STOP refuses a check-in (exit 5)" 5 $?
git -C "$T/cc" rm -q -r deadman; git -C "$T/cc" -c user.name=t -c user.email=t@t commit -q -m resume; git -C "$T/cc" push -q origin HEAD:refs/heads/cloud-config
A acquire >/dev/null 2>&1; expect "after STOP is lifted the check-in works again" 0 $?

# 9. push.sh: never held back; the branch is pushed and main moves to it when that is a fast-forward of safe paths
mkdir -p "$T/repo/tools"; cp "$D/tools/push.sh" "$D/tools/coord.py" "$D/tools/landing.py" "$T/repo/tools/"
git init -q -b work "$T/repo"; git -C "$T/repo" remote add origin "$T/origin.git"
git -C "$T/repo" fetch -q origin main 2>/dev/null; git -C "$T/repo" reset -q --hard FETCH_HEAD 2>/dev/null || true
cp "$D/tools/push.sh" "$D/tools/coord.py" "$D/tools/landing.py" "$T/repo/tools/" 2>/dev/null; mkdir -p "$T/repo/research"; echo note > "$T/repo/research/n.md"
git -C "$T/repo" -c user.name=t -c user.email=t@t add -A; git -C "$T/repo" -c user.name=t -c user.email=t@t commit -q -m "docs and tools"
PUSH_B() { (cd "$T/repo" && COORD_HOME="$T/b" COORD_LABEL=acctB COORD_RENEW_S=0 PUSH_SYNC_NOW=1 bash tools/push.sh >/dev/null 2>&1); }
A acquire --kind interactive >/dev/null 2>&1
PUSH_B; expect "push.sh pushes while another session works" 0 $?
git -C "$T/origin.git" rev-parse -q --verify refs/heads/work >/dev/null && ok "the session branch is on the remote" || bad "branch not pushed"
# tools/ changed without the fast checks: main waits; once ckpt.sh's marker says the checks passed on exactly these tools, main moves
git -C "$T/origin.git" log --oneline refs/heads/main | grep -q "docs and tools" && bad "main moved to unverified tools" || ok "main waits for tools nobody checked"
(cd "$T/repo" && python3 -I tools/landing.py mark tools >/dev/null 2>&1); PUSH_B
git -C "$T/origin.git" log --oneline refs/heads/main | grep -q "docs and tools" && ok "main moves once the tools are checked" || bad "main did not move after the mark"
# code (data/) waits for tools/test.sh's mark; docs alone go straight through
mkdir -p "$T/repo/data"; echo 'fun x() = 1' > "$T/repo/data/X.kt"; git -C "$T/repo" -c user.name=t -c user.email=t@t add -A; git -C "$T/repo" -c user.name=t -c user.email=t@t commit -q -m "code"
PUSH_B; git -C "$T/origin.git" log --oneline refs/heads/main | grep -q " code" && bad "untested code reached main" || ok "untested code stays on the branch"
(cd "$T/repo" && python3 -I tools/landing.py mark code data >/dev/null 2>&1); PUSH_B
git -C "$T/origin.git" log --oneline refs/heads/main | grep -q " code" && ok "tested code reaches main" || bad "tested code did not reach main"
# main moved under the branch (another session landed a note): the branch takes main in by itself (a clean merge) and lands again
git clone -q "$T/origin.git" "$T/other" 2>/dev/null; git -C "$T/other" checkout -q main; echo other > "$T/other/OTHER.md"
git -C "$T/other" add -A; git -C "$T/other" -c user.name=t -c user.email=t@t commit -q -m "other session"; git -C "$T/other" push -q origin HEAD:refs/heads/main
echo more > "$T/repo/research/m.md"; git -C "$T/repo" -c user.name=t -c user.email=t@t add -A; git -C "$T/repo" -c user.name=t -c user.email=t@t commit -q -m "more docs"
PUSH_B; expect "push.sh copes when main moved" 0 $?
[ -f "$T/repo/OTHER.md" ] && ok "the other session's work was merged into the branch" || bad "main's new work did not reach the branch"
git -C "$T/origin.git" log --oneline refs/heads/main | grep -q "more docs" && ok "and the branch landed on main after the merge" || bad "main did not get the branch after the merge"
# a conflicting change on main is never merged blind: the branch stays as it was, nothing is committed with conflict markers
git -C "$T/other" pull -q --no-rebase origin main >/dev/null 2>&1; mkdir -p "$T/other/research"; echo "main side" > "$T/other/research/n.md"
git -C "$T/other" add -A; git -C "$T/other" -c user.name=t -c user.email=t@t commit -q -m "main edit"; git -C "$T/other" push -q origin HEAD:refs/heads/main 2>/dev/null
echo "branch side" > "$T/repo/research/n.md"; git -C "$T/repo" -c user.name=t -c user.email=t@t commit -qam "branch edit"
PUSH_B
grep -rq '^<<<<<<<' "$T/repo/research" && bad "conflict markers were left in the tree" || ok "a conflicting merge is aborted, never committed"
[ "$(cat "$T/repo/research/n.md")" = "branch side" ] && ok "the session's own change is untouched" || bad "the session's file changed"

# 10. rotation: duties, ET slot times across daylight saving, missed-slot detection; the chat no longer rotates
python3 -I "$D/tools/rotation.py" selftest >/dev/null 2>&1; expect "rotation selftest (duties, DST, missed slots)" 0 $?
python3 -I "$D/tools/rotation.py" chat --tag acct-7542bf 2>/dev/null | grep -q "never stands down" && ok "no account stands down for the chat" || bad "rotation chat still rotates"

# 11. the UserPromptSubmit hook: a routine's own prompt is never logged; no lease, no relay, no chat stand-down text any more
H="$(mktemp -d)"; cp -r "$D/tools" "$H/tools"; git -C "$H" init -q 2>/dev/null; : > "$H/INBOX.md"
echo '{"prompt":"You are a Vigilant rotation worker for github.com/tjshea90/novig"}' | (cd "$H" && COORD_OFF=1 bash tools/capture_inbox.sh >/dev/null 2>&1); expect "capture_inbox ignores a routine's stored prompt" 0 $?
[ ! -s "$H/INBOX.md" ] && ok "the routine prompt was not written to INBOX.md" || bad "the routine prompt reached INBOX.md"
grep -q 'tell_tj.py relay\|HOLDS THIS REPO\|CHAT OWNER' "$D/tools/capture_inbox.sh" && bad "capture_inbox still relays or talks of a lease" || ok "capture_inbox has no relay and no lease"
rm -rf "$H"

# 12. a dormant account's routine stops (away.json in the future) and a past entry does nothing
DORM="$(mktemp -d)"; mkdir -p "$DORM/tools" "$DORM/research/coordination"; cp tools/coord.py tools/rotation.py "$DORM/tools/"
echo '{"acct-dorm": "2099-01-01T00:00:00Z", "acct-old": "2000-01-01T00:00:00Z"}' > "$DORM/research/coordination/away.json"
(cd "$DORM" && COORD_HOME="$DORM/home" COORD_KIND=routine COORD_LABEL=acct-dorm python3 -I tools/coord.py acquire --kind routine --task t >/dev/null 2>&1); expect "a routine of a dormant account refuses to start" 5 $?
(cd "$DORM" && COORD_HOME="$DORM/home" python3 -I tools/rotation.py checkin --tag acct-dorm >/dev/null 2>&1); expect "rotation checkin of a dormant account exits 5" 5 $?
COORD_HOME="$DORM/home" python3 -c "import sys; sys.path.insert(0,'$DORM/tools'); import coord; sys.exit(0 if coord.dormant_until('acct-old') is None else 1)"; expect "a past away time is not dormant" 0 $?
rm -rf "$DORM"

echo
if [ "$FAILS" -eq 0 ]; then echo "PASS $N checks"; else echo "FAILED $FAILS of $N checks"; exit 1; fi
