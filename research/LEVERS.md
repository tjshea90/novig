# Tuning levers: which study governs which setting, and what number settles the next change

Tj, 2026-10-21: every research project must be organized, remembered, reachable, and USED to tune the engine for maximum profit.
`research/INDEX.md` (generated: `python3 -I tools/research_index.py build`) lists every study and whether it is used.
This file is the other half: for each thing the engine can be tuned on, the studies behind it, where the setting lives, and the number that
settles the next move. Every ANALYST/ENGINEER run reads it (ROUTINE.md step 3a), moves ONE lever when its number is in
(class B gates in ROUTINE.md, a line in `research/review/CHANGES.md` with a revert rule), and edits the "Next number" cell it changed.
Moves already made are in `research/review/CHANGES.md`; the open ones are TASKS.md XOW1-XOW5.

| Lever | Where it lives | Studies | Next number that settles it |
|---|---|---|---|
| Trap guard window (taker bets only inside N h of the start; 6 h now) | auto-bet `trapEarlyHours` (`AutoBet.rules`, `BetSlider`) | `research/auto_bet_filters_2026-10-15.md`, `research/engine_profit_study_2026-10-21.md` | XOW3: per-kind windows at 200 settled each beyond 6 h (props 137, team totals 51, spreads 193, game totals 206 on 10-21) |
| Bid tiers, kinds and margins (proven / steady / risky) | `maker/BidTier.kt`, `MakerQuote`, bid slider | `research/profitable_types_2026-10-13.md`, `research/engine_profit_study_2026-10-21.md` | XOW4: no-sharp-book bids at 100 closes (CLV -0.15% on 30); NCAAF bids at 120 closes |
| Bid volume: deploy %, per-game limit, rung, order of posting | `vigGuards.bidDeployPct`, `MakerDesk`, `BidVolume.score` | `research/bid_volume_study_2026-10-21.md`, `research/bid_fill_speed_2026-10-14.md`, `research/bids_lead_time_2026-10-14.md` | XOW5: two days of fills at the new per-game limit, then deploy 100 -> 125; fill rate per 1000 postings (0.9 on 10-20) in the digest's THE ENGINE'S VOLUME |
| Prop fair calibration (Over bias, small-prop and thin-early cuts) | `PropHaircut`, `ThinEarlyHaircut` | `research/propline_vs_vigilant_2026-10-16.md`, `research/propline_history_study_2026-10-21.md`, `research/nhl_sog_matching_2026-10-13.md` | XOW1: bias column in `tools/lines_dataset.py`, 200 settled per side |
| Kalshi as a prop fair source (the only prop book that beat the market, +4.3 bp) | `ApiChain`, fair sources in `CloudSources` | `research/propline_history_study_2026-10-21.md`, `research/kalshi_plus_novig_2026-10-17.md` | XOW2: test on lines that carry it (`sharp_in_fair`, `n_fair_books`) |
| Which close counts as the truth for CLV | `CloseBackfill`, `tools/orders_clv.py`, the consensus columns | `research/clv_audit_2026-10-21.md`, `research/clv_consensus_2026-10-21.md` | share of bets with a confirmed close (27% at the audit); judge every other lever by CLV on confirmed closes first |
| Profit-proven types, stars and the edge floor | `ProfitProfile`, star rating, slider edge | `research/profit_types_2026-10-12.md`, `research/tan_bets_2026-10-12.md` (stars predict CLV, not results), `research/profit_card_2026-10-19.md` | re-run `tools/profit-profile.py` on every new history file; re-rank types weekly |
| Fair-line sources and the API chain (which book is sharp, how fresh) | `ApiChain`, `SourceScore`, `Scanner.SOURCE_ORDER` | `research/api_freshness_2026-10-12.md`, `research/odds_sources_2026-10-10.md`, `research/api_value_2026-10-21.md`, `research/propline_speed_freshness_2026-10-16.md` | `apiq` journal (CH58): a source's quote age and distance from Pinnacle; drop or demote one that loses on both |
| Books on one desk count once (Kambi group etc.) | `engine/BookCompany.kt` | `research/book_company_kambi_2026-10-11.md`, `research/book_company_2026-10-12.md` | add a book only on a documented provider or 80%+ identical lines over 100+ lines |
| Scan speed: how old the fair is when the list shows | `ScanGuards.hotRefresh`, `HotBudget` | `research/scan_speed_2026-10-15.md`, `research/cloud_review_2026-10-15.md` | list's median fair age in `status.json` `ScanTiming` (was 14.7 min) |

## Closed
Studies whose verdict is taken and where nothing is left to tune. Disposition after the dash. A closed study is reopened only if its number changes.

- `research/copy_sharps_2026-10-13.md` - copying sharp Novig traders is not possible and did not pay; following the sharp books is what Vigilant already does.
- `research/odds_api_net_2026-10-13.md` - The Odds API's Pinnacle is polled every 60-120 s, worse than PinnWire/pinnapi; do not buy a bigger plan.
- `research/propline_vs_parlayapi_pregame_2026-10-12.md` - keep both on the chain (ApiChain sleeps the lower); PropLine upgrade came first (now Pro).
- `research/subscription_advice_2026-10-12.md` and `research/recommendations_2026-10-15.md` and `research/api_budget_plan_2026-10-13.md` and `research/api_shopping_list_2026-10-12.md` and `research/api_logistics_2026-10-13.md` - what to buy or renew; acted on by Tj, superseded by `research/api_value_2026-10-21.md` (the live view is the engine's `ApiAdvisor`).
- `research/live_rapid_nba_plausibility_2026-10-12.md` and `research/live_light_2026-10-14.md` - live NBA taker stays on paper; live auto-bet is Tj's call (CLAUDE.md "Live games").
- `research/cloud_wallet_conflicts_2026-10-15.md` - no conflict found; the trading key's REST bucket is the only shared resource.
- `research/tan_fulltest_2026-10-12.md`, `research/tan_settings_audit_2026-10-12.md`, `research/tan_bids_2026-10-12_output.txt`, `research/tan_cluster_2026-10-12_output.txt` - the 10-12 audit and its raw output; findings were applied then (settings, bid fills healthy).
- `research/profit_types_raw_2026-10-12.txt` - raw tables of `profit_types_2026-10-12.md`.
- `research/boltodds_sample_2026-10-12.md`, `research/boltodds_sample2_2026-10-12.md`, `research/boltodds_live_v0.95.0_first_evidence_2026-10-12.md`, `research/boltodds_live_v0.95.1_evidence_2026-10-12.md`, `research/boltodds_live_v0.95.2_evidence_2026-10-12.md`, `research/boltodds_live_v0.95.3_evidence_2026-10-12.md` - BoltOdds samples and live-lane evidence; the verdict is in BOLTODDS_API.md.
- `research/novig_v0.91.0_first_evidence_2026-10-12.md`, `research/novig_v0.92.0_evidence_2026-10-12.md`, `research/streaming_v0.94.1_first_evidence_2026-10-12.md`, `research/diagnostics_v0.97.0_2026-10-13.md`, `research/diagnostics_v0.97.3_2026-10-14.md` - first-evidence logs of shipped features (NOVIG_API.md section 26-27, the streaming engine, Diagnostics).
- `research/scan_study_analysis_2026-10-03_v0.58.3_checkpoint.md`, `research/scan_study_analysis_2026-10-06_v0.70.1_checkpoint.md`, `research/scan_study_2026-10-10`, `research/research_file_2026-10-10`, `research/lab_analysis_2026-10-10_phone_v0.84.1.md` - the early scan-study passes (checkpoints of the analyses that follow them) and their data.
- `research/propline_clv_first_run_2026-10-16.md` - the first PropLine CLV run; the method is `tools/propline_clv.py`, results feed the CLV lever.
- `research/cloud_wallet_conflicts_2026-10-15.md`, `research/ACTIVE_LOG_2026-10-07.md`, `research/TASKS_TRIAGE_2026-10-18.md` - session logs; superseded by TASKS.md and CHANGES.md.
- `research/parlayapi_pro_2026-10-20` - ParlayAPI Pro docs and samples (TASKS.md CH47 builds on them).
