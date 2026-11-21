# AGENTS.md

<!-- handoff:learnings:begin -->
## Session Learnings (auto-updated by handoff)

### Implicit Rules
- Repo public; never commit keys; STOP ALL and dead-man switch never touched by Claude
- Settings changes: python3 tools/engine_set.py path=value --why; maker profile only via python3 tools/rfq_set.py (phone file ignored while rfq-maker-engine.json exists)
- Every change logged in research/review/CHANGES.md with revert command; TASKS.md box then bash tools/ckpt.sh after every step
- Landing engine code on main needs full bash tools/test.sh pass on exact code; engine picks up code via engine_set --reset (new runner on main)
- No Workflow tool or subagent fan-out for repo work; say each thing once; tell Tj only new things
- ScanSettings.engineBids requires maker=true, cloudDrives, cloudTrading LIVE, gates.cloudBids
- Journals: Releases cloud-archive-<upload day> assets rfq-<day>-run<id>.jsonl.gz; kinds CREATED/EXECUTED/QUOTE_SENT/TALLY; no-fair declines sampled 1/25
- Graded study: Release rfq-pnl-study graded.csv.gz (live flag, price, fair, buyer, pnl per $1 pot)
- Status/monitor: git fetch origin cloud-feed; status.json, engine-log.txt, parlays.json, desk.json, trades.json; cloud-config settings.json + rfq-maker-engine.json

### Key Decisions
- Decision: ON1 set gates.cloudTrading LIVE + gates.cloudBids true + maker true — Reason: engineBids requires maker; MK32 had set them off for parlay priority; Tj said turn all on live
- Decision: bidDeployPct 50 tried then restored to 100 — Reason: resting bids hold no cash at Novig; bidWalletPct is the real cap; cap protected nothing
- Decision: left liveParlays SHADOW — Reason: in-play parlays adverse-selected on 11-20 and graded in-play 2-leg is ~-1% +/-4.5 (no edge), 80-90% leg pocket -24%
- Decision: added RfqConfig.lossStopFromMs + Summary.pnlStop24h — Reason: stop was tripped by old-settings fills; research profile had zero live fills; keeps 30% stop, status keeps true 24h
- Decision: did not raise/remove loss stop — Reason: edge unproven; stop is backstop; Tj ordered it off once (MK17d) but profile was designed with it
- Decision: rejected adding leagues to settings.leagues for fair coverage — Reason: auto-bet, bids, live taker (pinnLiveBet true) and Kalshi lane share the scan; no league allow-list; autoBetMinStars 0; would trade unproven sports; use isolated fair-only second Scanner pass (FV1b)
- Decision: FV2 minFair .25, maxLeadMs 24h, adaptiveCash true score 5.7 — Reason: graded executed parlays at our quote fair+0.1pt: .25-.35 band +9.0% n1136 3 days same sign; lead cap was capital rule and cash idle; adaptive gate for when cash becomes limit; fair<.25 neutral/negative kept out
- Decision: monitoring by send_later every 45 min, stop ~10:00 UTC — Reason: repo says never poll in loops; tell Tj only about new things

<!-- handoff:learnings:end -->
