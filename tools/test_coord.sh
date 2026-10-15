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
B board 3 2>/dev/null | grep -q "hello from A" && ok "B reads A's note" || bad "B did not see the note"
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
PUSH_B; expect "push.sh holds a push back (exit 4) while A holds the lease" 4 $?
A release >/dev/null 2>&1
PUSH_B; expect "push.sh pushes once the lease is free" 0 $?
git -C "$T/origin.git" log --oneline refs/heads/main 2>/dev/null | head -1 | grep -q "two" && ok "the held-back commit reached the remote afterwards" || bad "commit two is not on the remote"

echo
if [ "$FAILS" -eq 0 ]; then echo "PASS $N checks"; else echo "FAILED $FAILS of $N checks"; exit 1; fi
