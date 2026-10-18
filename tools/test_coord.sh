#!/usr/bin/env bash
# test_coord.sh - proves tools/coord.py and the push.sh guard against a local bare repository standing in for GitHub. Two sessions (A, B) share it.
#   bash tools/test_coord.sh        prints one line per check and "PASS n checks" or the failing check; exit 1 on a failure.
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

A() { COORD_REPO="$T/work" COORD_HOME="$T/a" COORD_LABEL=acctA COORD_CACHE_S=0 COORD_RENEW_S=0 python3 -I "$D/tools/coord.py" "$@"; }
B() { COORD_REPO="$T/work" COORD_HOME="$T/b" COORD_LABEL=acctB COORD_CACHE_S=0 COORD_RENEW_S=0 python3 -I "$D/tools/coord.py" "$@"; }

# 1. the first session takes the lease (and creates the branch)
A acquire --kind interactive --task "test" >/dev/null 2>&1; expect "A takes a free lease" 0 $?
git -C "$T/origin.git" rev-parse -q --verify refs/heads/claude-coord >/dev/null && ok "the claude-coord branch exists on the remote" || bad "no claude-coord branch"
# 1b. a session that changes its label keeps its lease (found in live use: a renamed session was held back as "another session")
COORD_REPO="$T/work" COORD_HOME="$T/a" COORD_LABEL=acctA-renamed COORD_CACHE_S=0 COORD_RENEW_S=0 python3 -I "$D/tools/coord.py" check >/dev/null 2>&1; expect "a renamed session is still itself" 0 $?
# 2. a second session is turned away, and its push guard says no
B acquire >/dev/null 2>&1; expect "B is refused while A holds the lease" 3 $?
B check >/dev/null 2>&1; expect "B's push check says held back" 3 $?
A check >/dev/null 2>&1; expect "A's push check says go" 0 $?
A renew >/dev/null 2>&1; expect "A can renew" 0 $?
# 3. two racing takes: only one wins (release first so both see a free lease)
A release >/dev/null 2>&1
( A acquire >/dev/null 2>&1; echo $? > "$T/rcA" ) & ( B acquire >/dev/null 2>&1; echo $? > "$T/rcB" ) & wait
RA=$(cat "$T/rcA"); RB=$(cat "$T/rcB")
if { [ "$RA" -eq 0 ] && [ "$RB" -eq 3 ]; } || { [ "$RA" -eq 3 ] && [ "$RB" -eq 0 ]; }; then ok "a race for a free lease has exactly one winner (A=$RA B=$RB)"; else bad "race result A=$RA B=$RB"; fi
# make A the holder again for the next checks
if [ "$RB" -eq 0 ]; then B release >/dev/null 2>&1; fi
A acquire >/dev/null 2>&1
# 4. a stale lease is taken over, and the board says so
sleep 1
COORD_LEASE_S=0 B acquire >/dev/null 2>&1; rc=$?
# the lease file records leaseS as written by A (1200), so it is not stale yet: B must still be refused
expect "a lease younger than its own lease time is not taken" 3 $rc
python3 - "$T" <<'PY'
import json,subprocess,sys,os,time
T=sys.argv[1]
# age the heartbeat on the remote by hand: clone, edit, push
subprocess.run(["git","clone","-q","--branch","claude-coord",T+"/origin.git",T+"/edit"],check=True)
p=T+"/edit/lease.json"; d=json.load(open(p)); d["hb"]=int(time.time())-3600; d["leaseS"]=60; json.dump(d,open(p,"w"))
subprocess.run(["git","-C",T+"/edit","-c","user.name=t","-c","user.email=t@t","commit","-qam","age"],check=True)
subprocess.run(["git","-C",T+"/edit","push","-q","origin","HEAD:refs/heads/claude-coord"],check=True)
PY
B acquire >/dev/null 2>&1; expect "B takes over a stale lease" 0 $?
B board 5 2>/dev/null | grep -q "stale lease" && ok "the takeover is on the board" || bad "no takeover note on the board"
A check >/dev/null 2>&1; expect "A (stale, now beaten) is held back" 3 $?
# 5. interactive beats routine, routine never beats interactive
B release >/dev/null 2>&1
A acquire --kind routine --task "review" >/dev/null 2>&1; expect "A (routine) takes a free lease" 0 $?
B acquire --kind interactive >/dev/null 2>&1; expect "an interactive session takes over from a routine" 0 $?
A acquire --kind routine >/dev/null 2>&1; expect "a routine cannot take it back from an interactive session" 3 $?
# 6. --force is Tj's word and is logged
A acquire --kind interactive --force >/dev/null 2>&1; expect "--force takes it" 0 $?
A board 10 2>/dev/null | grep -qi "force" && ok "the forced take is on the board" || bad "no force note"
# 7. notes pass between sessions
A post "hello from A" "line of body" >/dev/null 2>&1; expect "A posts a note" 0 $?
B board 8 2>/dev/null | grep -q "hello from A" && ok "B reads A's note" || bad "B did not see the note"
OUT="$(B status 2>/dev/null)"; case "$OUT" in *"ANOTHER CLAUDE SESSION"*) ok "status tells B who holds the repo";; *) bad "status text: $OUT";; esac
# 8. EVERY failure lets work through: an unreachable remote
git init -q "$T/lonely"; git -C "$T/lonely" remote add origin "$T/does-not-exist.git"
COORD_REPO="$T/lonely" COORD_HOME="$T/c" COORD_LABEL=acctC python3 -I "$D/tools/coord.py" check >/dev/null 2>&1; expect "an unreachable remote does not hold a push back" 0 $?
COORD_OFF=1 B check >/dev/null 2>&1; expect "COORD_OFF lets everything through" 0 $?

# 9. push.sh: held back while another session holds the lease, goes through when it is free
mkdir -p "$T/repo/tools"; cp "$D/tools/push.sh" "$D/tools/coord.py" "$T/repo/tools/"
git init -q -b main "$T/repo"; git -C "$T/repo" remote add origin "$T/origin.git"
git -C "$T/repo" -c user.name=t -c user.email=t@t add -A; git -C "$T/repo" -c user.name=t -c user.email=t@t commit -q -m one
git -C "$T/repo" fetch -q origin main 2>/dev/null; git -C "$T/repo" reset -q --soft origin/main 2>/dev/null || true
git -C "$T/repo" -c user.name=t -c user.email=t@t commit -q --allow-empty -m two
PUSH_B() { (cd "$T/repo" && COORD_HOME="$T/b" COORD_LABEL=acctB COORD_CACHE_S=0 COORD_RENEW_S=0 bash tools/push.sh >/dev/null 2>&1); }
A acquire --kind interactive --force >/dev/null 2>&1
PUSH_B; expect "push.sh still pushes the branch (exit 0) while A holds the lease: work without the lease (Tj, 2026-10-18)" 0 $?
A release >/dev/null 2>&1
PUSH_B; expect "push.sh pushes once the lease is free" 0 $?
git -C "$T/origin.git" log --oneline refs/heads/main 2>/dev/null | head -1 | grep -q "two" && ok "the held-back commit reached the remote afterwards" || bad "commit two is not on the remote"

# 10. rotation: duties, ET slot times across daylight saving, missed-slot detection
python3 -I "$D/tools/rotation.py" selftest >/dev/null 2>&1; expect "rotation selftest (duties, DST, missed slots)" 0 $?

# 11a. wait-free: exits 0 at once when the lease is free; times out (exit 1) while a live session holds it; an interactive takeover of a routine leaves a rotation-paused note
B release >/dev/null 2>&1; A release >/dev/null 2>&1
(COORD_WAIT_POLL_S=1 A wait-free --max-min 0.05 >/dev/null 2>&1); expect "wait-free returns 0 when the lease is free" 0 $?
A acquire --kind routine --task "ENGINEER" >/dev/null 2>&1
(COORD_WAIT_POLL_S=1 B wait-free --max-min 0.05 >/dev/null 2>&1); expect "wait-free times out (1) while a routine holds the lease" 1 $?
B acquire --kind interactive >/dev/null 2>&1
B board 20 2>/dev/null | grep -q "rotation-paused label=acctA" && ok "taking a routine's lease posts rotation-paused for it" || bad "no rotation-paused note after the takeover"
B release >/dev/null 2>&1

# 11. the UserPromptSubmit hook: a scheduled routine's own prompt is never logged as Tj's words and never makes its session interactive; a human message does reach the lease step
H="$(mktemp -d)"; cp -r "$D/tools" "$H/tools"; git -C "$H" init -q 2>/dev/null; : > "$H/INBOX.md"
echo '{"prompt":"You are a Vigilant rotation worker for github.com/tjshea90/novig"}' | (cd "$H" && COORD_OFF=1 bash tools/capture_inbox.sh >/dev/null 2>&1); expect "capture_inbox ignores a routine's stored prompt" 0 $?
[ ! -s "$H/INBOX.md" ] && ok "the routine prompt was not written to INBOX.md" || bad "the routine prompt reached INBOX.md"
grep -q 'coord.py acquire --kind interactive' "$D/tools/capture_inbox.sh" && ok "a human message takes the lease (hook calls acquire --kind interactive)" || bad "capture_inbox does not take the lease"
rm -rf "$H"

# a dormant account's routine stops (away.json in the future) and a past entry does nothing
DORM="$(mktemp -d)"; mkdir -p "$DORM/tools" "$DORM/research/coordination"; cp tools/coord.py tools/rotation.py "$DORM/tools/"
echo '{"acct-dorm": "2099-01-01T00:00:00Z", "acct-old": "2000-01-01T00:00:00Z"}' > "$DORM/research/coordination/away.json"
(cd "$DORM" && COORD_HOME="$DORM/home" COORD_KIND=routine COORD_LABEL=acct-dorm python3 -I tools/coord.py acquire --kind routine --task t >/dev/null 2>&1); expect "a routine of a dormant account refuses to start" 5 $?
(cd "$DORM" && COORD_HOME="$DORM/home" python3 -I tools/rotation.py checkin --tag acct-dorm >/dev/null 2>&1); expect "rotation checkin of a dormant account exits 5" 5 $?
COORD_HOME="$DORM/home" python3 -c "import sys; sys.path.insert(0,'$DORM/tools'); import coord; sys.exit(0 if coord.dormant_until('acct-old') is None else 1)"; expect "a past away time is not dormant" 0 $?
rm -rf "$DORM"

echo
if [ "$FAILS" -eq 0 ]; then echo "PASS $N checks"; else echo "FAILED $FAILS of $N checks"; exit 1; fi
