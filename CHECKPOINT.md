# CHECKPOINT 2331 — read me first, then TASKS.md

**Written:** 2026-10-02T15:19:31Z · **tests:** all 3 fast checks green
**Branch:** `ccr-ea5bf769-s5xd3t` · **builds on:** `81249a7e` (this checkpoint is the commit after it)

## Just done
CI flake fixed: BackgroundTrimTest counted the scanner's own memory-pressure trims on CI's busy JVM (reproduced with a busy probe); it now runs on a roomy MemoryGuard probe

## Do this next
wait for CI green on this head, re-run ship.sh, trigger release.yml, verify, record, answer Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  7825365d ckpt 2330: pre-release: v0.44.2: manual bets have no minimum edge (auto-bet keeps its r
  8439d8c3 ckpt 2329: floor fixes: CycleRecorderTest pin follows AS2's event line; AutoResumeAppTe
  62a03929 ckpt 2328: AS4 ticked (5 mutants killed), RESEARCH §46 note
  245f416f ckpt 2327: AS4 code+tests: CNO's book check counts one vote a company (state sites aver
  71fb5827 ckpt 2326: AS3 ticked; NOVIG_API.md records the live 423 account lock (signed routes on
  c063a3e7 ckpt 2325: AS3 code+tests: slow scan = Novig's 423 on the key -> public routes (4-6/s v
  5af19d8b ckpt 2324: AS2 done and ticked (6 mutants killed)
  361848a5 ckpt 2323: AS2 code+tests: LaunchGate (per process) decides a fresh launch: restored ne
  6c70d001 ckpt 2322: AS1 done: Check odds now and pull to refresh resume a paused scanner (the ot
  831f36c4 ckpt 2321: AQ2 ticked: notification off main + FoundCount, frame meter in Diagnostics; 
```

(1 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
