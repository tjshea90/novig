# CHECKPOINT 96 — read me first, then TASKS.md

**Written:** 2026-09-20T06:01:40Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `3047deb` (this checkpoint is the commit after it)

## Just done
Built the first real app code: 'Vigilant' — a 3-module Gradle project (engine/data plain-Kotlin, app Android+Compose). engine: devig math (multiplicative/additive/power/Shin), consensus (prefer sharp book else average, per Tj's own instruction), Novig fee model (parlay explicitly Unknown, never silently $0), EV calculator — 29 tests, all real and green, caught+fixed a real bug in the power/Shin solvers along the way (required positive margin, a bad test fixture exposed it). data: real OAuth2+REST+WebSocket client for Novig's documented API, real client for The Odds API, sample-data fallbacks, and EvScanner tying it together — 25 more tests, all green, including one proving a live market's positive raw edge goes net-negative once Novig's fee is applied. app: basic Compose UI (OpportunitiesScreen) wired to sample data by default with a visible SAMPLE DATA banner so it's never mistaken for live. Added .github/workflows/ci.yml since this container has no Android SDK and can't verify the app module locally — CI installs one via android-actions/setup-android. Updated BRIEF.md's Toolchain/Platform/Locked-architecture-decisions sections with everything decided (Kotlin+Compose, JDK21/Gradle8.14.3/AGP8.13.2/Kotlin2.3.10, compileSdk36/minSdk30, applicationId com.tjshea.vigilant).

## Do this next
Push this and confirm the CI workflow actually goes green on the app module — this container could not verify Compose/manifest/AGP compile locally, only engine+data's 54 tests. Then tell Tj plainly: the beta runs today on sample data (nothing needed), but going live needs him to (1) sign up for a free The Odds API key and (2) contact Novig directly about official API access — still unconfirmed whether that's free (RESEARCH.md §10).

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  3edb9b3 ckpt 29: Wrote Tj's 'begin basic coding' request into TASKS.md (raw message already capt
  c361581 ckpt 26: Deep-dived Odds Assist Pro against Novig, hands-on (not just secondhand): loade
  4ce2d00 ckpt 21: Wrote Tj's Odds Assist Pro deep-dive request into TASKS.md (raw message already
  5787e5b ckpt 18: Deep research on positive-EV betting for Novig, written to RESEARCH.md (new per
  1c3d806 ckpt 15: Wrote Tj's positive-EV research request into TASKS.md in his own words as untic
  e870ad3 ckpt 12: Stood up the full checkpoint/handoff system for novig, adapted from fantasy-foo
```

(66 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
