# CHECKPOINT 2330 — read me first, then TASKS.md

**Written:** 2026-10-02T15:13:58Z · **tests:** all 3 fast checks green
**Branch:** `ccr-ea5bf769-s5xd3t` · **builds on:** `8439d8c3` (this checkpoint is the commit after it)

## Just done
pre-release: v0.44.2: manual bets have no minimum edge (auto-bet keeps its rules); Novig's 423 codes named, a locked market skips one bet; Check odds now prices every open bet whatever the scanner and resumes a paused scanner, as pull to refresh does; auto-bet stays on when you switch apps (off only on a fresh launch or restart); a scan without the key says why it's slower; CNO counts each sportsbook company once (Hard Rock's state sites are one book); scan notification off the main thread; frame meter in Diagnostics (versionCode 81, v0.44.2)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v0.44.2), then run: bash tools/record-release.sh v0.44.2 81 "v0.44.2: manual bets have no minimum edge (auto-bet keeps its rules); Novig's 423 codes named, a locked market skips one bet; Check odds now prices every open bet whatever the scanner and resumes a paused scanner, as pull to refresh does; auto-bet stays on when you switch apps (off only on a fresh launch or restart); a scan without the key says why it's slower; CNO counts each sportsbook company once (Hard Rock's state sites are one book); scan notification off the main thread; frame meter in Diagnostics"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  8439d8c3 ckpt 2329: floor fixes: CycleRecorderTest pin follows AS2's event line; AutoResumeAppTe
  62a03929 ckpt 2328: AS4 ticked (5 mutants killed), RESEARCH §46 note
  245f416f ckpt 2327: AS4 code+tests: CNO's book check counts one vote a company (state sites aver
  71fb5827 ckpt 2326: AS3 ticked; NOVIG_API.md records the live 423 account lock (signed routes on
  c063a3e7 ckpt 2325: AS3 code+tests: slow scan = Novig's 423 on the key -> public routes (4-6/s v
  5af19d8b ckpt 2324: AS2 done and ticked (6 mutants killed)
  361848a5 ckpt 2323: AS2 code+tests: LaunchGate (per process) decides a fresh launch: restored ne
  6c70d001 ckpt 2322: AS1 done: Check odds now and pull to refresh resume a paused scanner (the ot
  831f36c4 ckpt 2321: AQ2 ticked: notification off main + FoundCount, frame meter in Diagnostics; 
  be4d5c86 ckpt 2320: AS written to TASKS.md (Tj 06:14Z: auto-resume scanner on Check odds now/pul
```
