# CHECKPOINT 4747 — read me first, then TASKS.md

**Written:** 2026-11-20T02:28:22Z · **tests:** all 11 fast checks green
**Branch:** `claude/optimistic-curie-aoglz9` · **builds on:** `474452154` (this checkpoint is the commit after it)

## Just done
pre-release: MK29: parlay legs graded from final scores; in-play legs blue not green (versionCode 485, v1.0.145)

## Do this next
Trigger .github/workflows/release.yml via mcp__github__actions_run_trigger, confirm it goes green via mcp__github__get_release_by_tag (tag v1.0.145), then run: bash tools/record-release.sh v1.0.145 485 "MK29: parlay legs graded from final scores; in-play legs blue not green"

*(resuming? CLAUDE.md's "FIRST ACTION OF EVERY SESSION" comes before "Starting a session" — do that one first, or autosave stays off all session.)*

## Uncommitted right now
     M CHECKPOINT.md

## Last ten checkpoints
```
  474452154 ckpt 4746: pre-ship: MK29: parlay legs graded from final scores; in-play legs blue not
  68d5bcadc ckpt 4745: MK29: grade parlay legs from final scores (ParlayLegScores) so finished leg
  805dc24a3 ckpt 4744: MK28: quote markup was 3.9 pts over fair vs the market's +0.3: liveMinRetur
  6ab5b5f5c ckpt 4743: MK27: full suite green, protected mapping lands
  47991ec5d ckpt 4742: MK28b: correction: journal under-samples no-fair declines; live legs and ca
  fd84d3160 ckpt 4741: MK28: liveMinReturn .008 (rfq_set, stake cap unchanged)
  092b7c49b ckpt 4740: MK28: live floor .03 -> .008, stake cap 6 (Tj wants fills)
  1240d5302 ckpt 4740: MK27: longshotsOnly off (Tj's filter), protected markets use the unprotecte
  0934ea2dc ckpt 4739: MK21 evidence check: extrapolated legs mostly in same-game combos (none quo
  9c900f1d9 ckpt 4738: MK26: exact replay: engine quotes what Tj's caps allow (25% of the morning'
```
