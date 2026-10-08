# CHECKPOINT 2727 — read me first, then TASKS.md

**Written:** 2026-10-08T01:38:20Z · **tests:** all 4 fast checks green
**Branch:** `ccr-f7044881-1c7epp` · **builds on:** `ee617f03` (this checkpoint is the commit after it)

## Just done
Pinnodds live: app wiring done (ApiProvider.PINNODDS, ScanSettings pinnLive*, VigilantApp runner/trader/tick, Settings page PINNODDS with Test key, PinnText, Diagnostics block, ScannerFilter.PINNODDS); trigger modes SCORE(default)/MOVE/STANDING after the replay showed price-only moves reverting (EV +11.7% at decision, -13.8% at +120s, n=10); score frame precedes Pinnacle's reprice by median 1.8 s; fixed 2 real bugs found by tests (OkHttp skips network interceptors for websockets so the deflate offer is stripped with an application interceptor; start race). 69 pinnodds tests + SettingsPagesTest green

## Do this next
NEXT: PW8: UI test for the Pinnodds page, NOVIG_API.md section 21 (ws place/cancel verbs, X-Novig-WS-Compress), RESEARCH.md section 116, CLAUDE.md pointer, save study tapes (gz) to research/pinnodds_2026-10-08/, stop the recorder (pid file in scratchpad/pinn/rec.pid) BEFORE release, full floor, version bump + BUILDLOG, ship.sh, release.yml, tell Tj

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  6136ef32 ckpt 2726: PINNODDS LIVE (TASKS.md PW*): read pinnodds docs + tested the key (trial_dem
  a4df2b62 ckpt 2725: Made the ten-sources research resumable by any session/account: tools/resear
```

(21 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
