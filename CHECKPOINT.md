# CHECKPOINT 18 — read me first, then TASKS.md

**Written:** 2026-09-20T04:42:17Z · **tests:** all 1 fast checks green
**Branch:** `claude/positive-ev-sports-research-n6jum5` · **builds on:** `d9866d9` (this checkpoint is the commit after it)

## Just done
Deep research on positive-EV betting for Novig, written to RESEARCH.md (new permanent file): Novig's own official developer API (docs.novig.com — REST/WebSocket/GraphQL, OAuth2, generous rate limits, built for algo traders) is the key cost lever vs $79-399/mo third-party resellers (SharpAPI, OpticOdds, Betstamp, SportsGameOdds) that resell the same underlying data. Also covered: devigging math (multiplicative/additive/power/Shin formulas), Novig's fee structure (free pre-game straight trades, small taker fee on live/parlay), EV math adapted to Novig's probability-price quoting, Android real-time architecture (foreground service + single WebSocket + Doze-aware battery discipline per BRIEF.md's Moto G 2026 constraint), and the competitive landscape (OddsJam $199.99/mo Gold; a free tool called Odds Assist Pro already covers Novig and is worth trialing before building). TASKS.md updated with findings summary per box.

## Do this next
Ranked open items in RESEARCH.md §10 need resolving before architecture is locked: (1) confirm with Novig directly whether API credentials are actually free/how to get them — this is the single biggest unknown gating the whole cost model, (2) verify whether SharpAPI's free tier includes Pinnacle in its raw-odds set (determines if the reference-odds leg is $0 or needs The Odds API's $30/mo tier), (3) hands-on trial Odds Assist Pro against Novig to see if it already satisfies the ask. Do not start app code or BRIEF.md architecture decisions until Tj has weighed in on these, per BRIEF.md's TBD sections and this request's own scope.

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  1c3d806 ckpt 15: Wrote Tj's positive-EV research request into TASKS.md in his own words as untic
  e870ad3 ckpt 12: Stood up the full checkpoint/handoff system for novig, adapted from fantasy-foo
```

(2 automatic checkpoint(s) since the last deliberate one — the
session was still mid-step. `git diff` against it shows what changed.)
