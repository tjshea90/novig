# AGENTS.md

<!-- handoff:learnings:begin -->
## Session Learnings (auto-updated by handoff)

### Implicit Rules
- Branches: cloud-config (settings.json + rfq-maker.json), cloud-feed (status.json, engine-log.txt), phone-data (phone/bets, phone/study), api-docs (docs mirror)
- Repo secrets: SGO_API_KEY, ODDSPAPI_API_KEY, ODDSPAPI_FREE_API_KEY, UNABATED_API_KEY, PINNWIRE_API_KEY, PINNAPI_API_KEY, PROPLINE_API_KEY, ODDS_API_KEY, PARLAY_API_KEY
- Repo CLAUDE.md: ckpt after each step (bash tools/ckpt.sh), ship.sh then release.yml then record-release.sh, no Workflow/subagent fan-out, never commit keys, main landing needs test stamps on exact tree
- Engine commands: python3 tools/engine_set.py --reset --why ... replaces runner; python3 tools/rfq_set.py mode=OFF|SHADOW|LIVE; tests: bash tools/test.sh :cloud:test :data:test
- Evidence sources: RFQ journals rfq-DAY-run<id>.jsonl.gz in Releases cloud-archive-YYYY-MM-DD (archive every 20 min, UTC day tag); python3 -I tools/rfq_summary.py run FILES; python3 -I tools/rfq_legs.py catalog|legs; unauthenticated api.github.com release listing works from container
- Maker maths: collateral = stake*(1-P)/P; maker EV per $1 pot = price - fair; EV per collateral = (price-fair)/(1-price); fee 0.055*(-ln P)*stake on taker from 2026-11-19; quote price = parlay win probability; lowest price wins; 1 s last look; 99% confirm rate or 5-min lockout
- Tj must open Novig app on phone before Thu 2026-11-19 03:00 ET (device check expiry); STOP ALL and dead-man rules never touched by sessions

### Key Decisions
- Decision: maker lives in GitHub engine only, phone monitors — Reason: Tj standing rule (processing on GitHub, phone is control center)
- Decision: mode file rfq-maker.json on cloud-config, default OFF, now SHADOW — Reason: old phone builds cannot overwrite it; plain commit changes it; nothing sent in SHADOW
- Decision: LIVE only by evidence rule (200+ priced trades, median market margin above ours plus cushion, plus MK5 realized-outcome check), first caps $12 stake / $45 per parlay / $90 open collateral, margin 8%, tell Tj before switching — Reason: winner's curse + 99% confirm-rate lockout risk + real money
- Decision: price only independent legs (product of leg fairs), decline same-game, in-play, thin/stale-fair, more than maxLegs — Reason: correlation unmodelled; engine has no live fair
- Decision: journal keeps every priceable RFQ and every trade, 1-in-25 sample of the rest, plus 5-minute TALLY row; unpriced trades keep outcomeIds — Reason: ~60 RFQs/s would be gigabytes/day; tally keeps true shares
- Decision: pending window 2 min / 20000 entries — Reason: 600 entries = 10 s at this rate, trades were not matched to their RFQ
- Decision: keep SHADOW, do not switch LIVE yet — Reason: only 164 priced trades, mostly two-leg; tail of 12%+ margins ambiguous (weak competitors vs wrong fair); wallet ~$200 caps value at ~$5-15/day

<!-- handoff:learnings:end -->
