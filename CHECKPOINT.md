# CHECKPOINT 4251 — read me first, then TASKS.md

**Written:** 2026-10-25T22:54:54Z · **tests:** all 9 fast checks green
**Branch:** `ccr-e58c5a58-3qyzld` · **builds on:** `28e1d9889` (this checkpoint is the commit after it)

## Just done
pre-ship: v1.0.85 PX11: a filled order whose fills Novig would not let us read yet is no longer called 'nobody was selling' (nor put back on the list to be bet twice); the bet sheet lists books best odds first and marks in red the books whose own odds say the bet is not +EV

## Do this next
ship.sh gates and releases this

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md
     M app/build.gradle.kts

## Last ten checkpoints
```
  28e1d9889 ckpt 4250: PX11a/b/c done in code (fill misreport fixed phone+engine, NOFILL keeps the
  694adcf71 ckpt 4249: PX11c bid study (keep the sharp veto: 0 fills against a sharp book, no-shar
  5f0740331 ckpt 4248: PX11a root cause + fix (FILLED orders whose fills were rate-limited were re
  6a646d461 ckpt 4247: PX11a: novig-probe.yml passes default statuses (Gradle refuses empty --args
  3e9157ea2 ckpt 4246: PX11a: novig-probe.yml + cloud/OrdersProbeMain.kt (Novig's record of reject
  3081884d7 ckpt 4245: PX11 written into TASKS.md (5 parts)
  4aa23e7c5 ckpt 4244: v1.0.84 released + recorded; phone adopted Pinnodds off (its own copy now s
  cddc690ee ckpt 4243: PX10 findings recorded (Pinnacle census numbers, migration gap); v1.0.84 re
  835ab9fd2 ckpt 4242: pre-release: v1.0.84 PX10: phone keeps a Claude session's settings change i
  a4c29748b ckpt 4241: pre-ship: v1.0.84 PX10: phone keeps a Claude session's settings change it h
```
