# active-memory handoff: Vigilant bids (Volume mode), repo tjshea90/novig

**Handoff #1** · 2026-10-14 · Lineage: #1 (2026-10-14): from "Prompt is too long" resume through v0.99.7 to v0.99.16: negative-EV list fix, week/any-time wallet-filling bids, longer rests, per-game spread, progressive bidding, quick/full scans, Volume mode with stake scale-up

## 0. Instructions for Claude (read first)

You are continuing work from a previous chat. That chat is gone; this file is the complete context and the source of truth.

1. Read this whole file before replying.
2. Follow sections 3 (Style), 4 (Hard rules) and 5 (Corrections) in every reply, for the rest of this chat. They override your defaults.
3. Use the values in section 8 exactly. Never round, re-estimate, or "correct" them.
4. Do not suggest anything listed in section 7 (Changed / rejected) again unless the user brings it up.
5. Code word: Start every reply with exactly "Yes Boss!"
6. Your first reply: at most 5 lines covering the goal, the current state, and the next step (section 11). Mention any files from section 13 that were not attached. Ask the questions in section 12 if there are any. End with "Ready to continue with <next step>?" Then wait for the user's go.

## 1. Mission
- **Goal:** Vigilant (Tj's Android app for the Novig sportsbook, repo `tjshea90/novig`, package `com.tjshea.vigilant`, Android 16 / API 36, Moto G 2026) posts hundreds to thousands of small positive-EV bids on Novig at all times, the kinds most likely to fill first, spread over many games and bid types, and fills the wallet.
- **Done looks like:** bids up fill most of the wallet (target 90%), many fills per hour, edge floors respected (1.5% / 2.5% EV), no game holds more than its limit, scans stay short enough that bids are not starved.
- **Why it matters / context:** Tj is experimenting with lower EV margins for large numbers of fills. The app is built to profit using Novig.

## 2. About the user (as relevant to this work)
- Tj (tjshea90@gmail.com). Owner of the app and wallet. Reads Diagnostics files from the phone (Settings › Diagnostics & about) and sends them as attachments; reports problems with screenshots.
- Wants volume and fills, tolerates smaller edges, wants Claude usage kept efficient but longer sessions allowed when they make a better product.
- Works across several Claude accounts and sessions on one repo; handoff through the repo is lossless (see section 4).

## 3. Style & communication
- **Language:** English.
- **Tone:** direct, plain; explain causes from the data, then what changed, then what to check.
- **Reply length:** short sections; state what is confirmed vs not (never claim a release works on the phone: it has not been run from the container).
- **Formatting:** bold lead-ins and short bullets; release links as bare URLs on their own line, never inside a code block.
- **Working style:** act on the request; read Tj's Diagnostics file before guessing; ship via the repo's pipeline; checkpoint after every step.
- **Avoid:** subagents/Workflow tool, guessing without the file, claiming untested behavior works.

## 4. Hard rules (word for word)
1. "Post as many bids as possible. Fill up the wallet. Hundreds of bids. All sports are allowed. All times are allowed. All props are allowed. Fill it with popular sports and market types first. As long as risky bids have a higher ev ask, it is fine"
2. "I only want so many bids/money on each game. Make it diversify bids among many games and bid types"
3. "The key is volume. I want hundreds or thousands of bids up at any time, on markets and types of bids most likely to be filled put up first. It can go down to 1.5% positive EV on the safest bid types and 2.5% positive EV on riskier ones. The goal right now is pure volume and bid fills. I'm going to experiment with lower positive EV margins but large numbers of fills. Optimize the bid types for smaller positive EV margins but maximum chance of many bids being filled rapidly"
4. Every request is for Vigilant (Novig). `mgm/` is frozen at v0.17.0; never edit it (repo CLAUDE.md).
5. Do not use the Workflow tool or fan out subagents (Tj, 2026-10-11, repo CLAUDE.md).
6. First action of a session: `bash tools/install-hooks.sh`. After every completed step: `bash tools/ckpt.sh "did" "next"`. Write new requests into `TASKS.md` in Tj's words before code.
7. Release pipeline: `bash ship.sh "note"` (full gate; versionCode must exceed every BUILDLOG entry), then trigger `release.yml` on `main` (`mcp__github__actions_run_trigger`), confirm with `mcp__github__get_release_by_tag`, then `bash tools/record-release.sh vX.Y.Z <code> "note"`, then send Tj the link as plain text on its own line.
8. Never commit a key. No Claude session opens the Pinnodds, BoltOdds or Kalshi sockets with Tj's keys; no Pinnodds API use by sessions.
9. The app's per-bid, per-game and money limits are Tj's: do not lift his limits (odds, per-game bids) without his word; ask in one line.
10. Every new behavior gets a switch back to the old one and a Diagnostics line.
11. `ScanSettings` is near the 255-argument-slot limit (test `ScanSettingsSizeTest` fails at 246): new settings go in `ScanGuards` (`vigGuards`), not top-level.
12. Use the free GitHub features (Actions, release workflow) for builds; do not poll with sleeps: use `send_later` check-ins and read notifications.
13. Never `curl` the internet from the shell for release checks (a hook blocks it): use the GitHub MCP tools.

## 5. Corrections log
| # | Claude did | The user wanted |
|---|---|---|
| 1 | Chat ended with "Prompt is too long" (context window 977k of 1M used) | A fresh, resumable session; the repo's CHECKPOINT/TASKS handoff carries the state |
| 2 | v0.99.8 widened the scan reach to 10 days: scans took 496 s, fair odds went stale, few bids | "It's broken. It placed even fewer bids on this version. The scan takes way too long." (fixed v0.99.13: quick 48 h scans between full ones) |
| 3 | v0.99.11 bids were taken down every minute, rested ~48 s, piled onto the same games | Longer rests; "I only want so many bids/money on each game" (fixed v0.99.12) |
| 4 | v0.99.14 Volume mode removed the stake scale-up: wallet stayed about half used | Fill the wallet (fixed v0.99.16: new stakes scale toward the target) |
| 5 | Left bids from waiting for every odds source of a league to answer | "can't it put up bids progressively as the scan is going?" (fixed v0.99.13) |

## 6. Decisions
| Decision | Why |
|---|---|
| Volume mode on by default (schema 21 migration), switch on Bids tab | Tj's stated goal is pure volume |
| EV floors `BidVolume.SAFE_EV` 0.015 (proven/steady with a sharp book), `RISKY_EV` 0.025 (risky tier, small market, or no sharp book) | Tj's 1.5% / 2.5% |
| Price = fair / (1 + floor): the highest allowed price | Thin edges fill ~2x faster (2-3% EV: 16.6 fills/100 bid-h vs 4-6%: 7.5) |
| Order by fill speed score (`BidVolume.score`: lead time x kind x league x leads) | From 24,267 bids: research/bid_fill_speed_2026-10-14.md |
| Volume limits: 3,000 bids, 250 posts a pass, steady/risky need 3+ agreeing books, no sharp book required | Volume; the risky floor covers lines without a sharp book |
| New stakes scale toward the wallet target (deployScale, max 4x, Kelly ceiling and per-bid max still hold); wallet-filling passes and deeper rung off in Volume mode | Fill the wallet without widening margins |
| Per-game limits for bids (`vigGuards.bidPerGameBids`, `bidPerGameDollars`, defaults 10 and $10, Bids tab chips) and `BidSpread.interleave` (each game's best first, kinds in turn) | Diversify |
| A resting bid stays through a transient "fair too old" skip until its own expiry (`MakerPlan.transientSkip`); wallet-filling bids requote only after 3x the usual rise | Stop mass cancels |
| Wallet-filling far bids (>=12 h) may rest on a fair up to 30 min old; 1-12 h up to 15 min (Volume) | Longer rests |
| `FarLane`: background scan alternates a 48 h quick scan with a full-reach scan at most every 15 min; bids past a quick scan's reach stay until expiry (`reachUntilMs`) | Scan took 8 min at 10 days |
| Novig books kept 90 min for unplanned markets; Volume mode bids far (>=6 h) games from a book up to 90 min old | Far supply was starved |
| Bid while the scan runs (`bidWhileScanning`) | Progressive bidding |

## 7. Changed / rejected
- Margin 2.91% under fair (+ tier extras, +4/+6/+8 wallet-filling margins) → Volume mode floors 1.5% / 2.5% (Tj, 2026-10-14)
- Trap guard 21 h for bids → no start-time limit in Volume mode (the setting still exists, Volume overrides it)
- Reach 7 days → 10 days (`ScanSettings.BID_REACH_DAYS`), made affordable by quick scans
- Stake scale off in Volume (v0.99.14) → on (v0.99.16)
- ❌ Reading Tj's Diagnostics from a screenshot alone: always ask for / read the file
- ❌ `curl` for release checks (blocked by a hook): use GitHub MCP

## 8. Data & facts (exact)
| Release | versionCode | What |
|---|---|---|
| v0.99.7 | 266 | Novig scan errors clear themselves; deeper second bids |
| v0.99.8 | 267 | Bids read a week of games (chip no longer shortens the scan) |
| v0.99.9 | 268 | +EV list re-prices from Novig live prices every 15 s during a scan |
| v0.99.10 | 269 | Wallet-filling bids may rest behind the best bid; week-pass skip reasons |
| v0.99.11 | 270 | Last wallet-filling pass: any time, any kind, no sharp book; 10-day reach |
| v0.99.12 | 271 | Longer rests; per-game bid limits; interleave |
| v0.99.13 | 272 | Bid while the scan runs; FarLane quick scans |
| v0.99.14 | 273 | Volume mode |
| v0.99.15 | 274 | Far books kept 90 min; Volume far book age 90 min |
| v0.99.16 | 275 | Volume mode stake scale-up (latest, recorded) |

Release URL pattern: https://github.com/tjshea90/novig/releases/tag/vX.Y.Z (latest https://github.com/tjshea90/novig/releases/tag/v0.99.16).

Constants: `BidVolume.SAFE_EV` 0.015 · `RISKY_EV` 0.025 · `MAX_BIDS` 3000 · `POSTS_PER_PASS` 250 · `MID_FAIR_MAX_AGE_MS` 15 min · `MIN_AGREEING` 3 · `MakerDesk.DEPLOY_FAR_FAIR_MS` 30 min · `Scanner.BOOK_KEEP_MS` 90 min · `MakerLines.VOLUME_FAR_BOOK_AGE_MS` 90 min · `MakerLines.MAX_BOOK_AGE_MS` 20 min · `FarLane.NEAR_HOURS` 48 · `FarLane.FAR_EVERY_MS` 15 min · settings schema 21 · `ScanSettings.BID_REACH_DAYS` 10.

Fill speed (fills per 100 bid-hours, 24,267 bids): player props <6 h 30.1; props 6 h+ 11.3; team totals 5.8; period lines 3.1; spreads 1.5; game totals 1.3; WNBA 33.0; NHL 22.6; MLB 16.1; NFL 10.3; NCAAF 3.4; ATP 1.5; WTA 0.0; lead <1 h 25.7; 1-3 h 16.9; 3-6 h 15.7; 6-12 h 8.9; 12-24 h 5.6; 24-72 h 1.0; leads its side 16.2 vs behind 2.2.

Tj's current settings seen in the v0.99.14 file: 5 bids per game limit, no bid longer than +120, 1/8 Kelly of $185 bankroll up to $3.50 a bid, Profit-proven only on (skips NFL player props bids by history), sockets: only Pinnodds off, Novig market socket on. Wallet about $139.75.

Observed (v0.99.14 file, mid full scan): 93-121 bids up (about $18-74), 63 games with bids; skip reasons: ~167 priced too far behind the best bid, ~110 longer than +120, ~104 already bet or bid, ~78 fair too old.

## 9. People, terms & names
- **People:** Tj: owner and only user.
- **Terms & nicknames:** Volume mode = `vigGuards.bidVolume`; quick scan = `nearScan` 48 h scan (`FarLane`); wallet-filling pass = `deployFor` passes (off in Volume mode); transient skip = fair too old this pass; trap guard = early-bid limit; kill switch = STOP ALL.
- **Names in use:** `data/.../novig/trading/maker/` `BidVolume.kt`, `BidSpread.kt`, `MakerQuote.kt` (MakerRules, MakerQuote, MakerPlan, MakerLines), `MakerDesk.kt`; `data/.../scanner/FarLane.kt`, `Scanner.kt`, `ScanSettings.kt` (ScanGuards at the bottom); `app/.../MakerRunner.kt`, `MainViewModel.kt` (Diagnostics wallet section), `ui/MakerScreen.kt`; `research/bid_fill_speed_2026-10-14.md`; tools `tools/test.sh`, `ship.sh`, `tools/ckpt.sh`, `tools/record-release.sh`; Diagnostics uploads in `research/uploads/2026-10-14/`.

## 10. Work state
| Item | Status | Version / location | Notes |
|---|---|---|---|
| Volume mode, quick scans, progressive bidding, per-game spread, longer rests | released | v0.99.16 | Not yet seen on a phone after v0.99.16 |
| Tests | green | 3,580 tests at ship | `KillSwitchAppTest` "Pause, a pull to refresh..." can flake once on the "Resumed" toast; passes alone |
| TASKS.md | up to date | sections TWD, TWP, TWR2, TVM, TVM4, TVM5 | all ticked except old unrelated items |
| CHECKPOINT.md | current | ckpt 3274+ | next: read Tj's next Diagnostics |

## 11. Next steps
1. **Next action:** wait for / read Tj's next Diagnostics from v0.99.16 (`WALLET DEPLOYMENT`: VOLUME MODE lines, resting dollars vs target, spread, skip reasons; `MAKER` log lines; `Scan speed`; bid-hours by lead time) and find which limit binds.
2. If the wallet is still short: check per-bid max ($3.50), Kelly ceiling, 5 bids per game, +120 odds limit, Profit-proven skips; ask Tj before changing his limits.
3. Watch for app lag from `maker.json` with thousands of bids (ended unfilled bids kept 6 h) and for Novig order-rate (8 orders/s) pressure; shorten retention if needed.
4. Check fills per bid-hour and EV kept at fill by type against `research/bid_fill_speed_2026-10-14.md`; re-rank `BidVolume` factors if a newer file says so.

## 12. Open questions ⚠️
- Should the per-game bid limit (now 5) and the longest odds (+120) be raised for Volume mode? Only Tj decides; candidates: per-game 20 or no limit, odds +175 or longer.
- CrazyNinjaOdds times out every 20 s in the files: not caused by this work; ask only if Tj wants it handled.

## 13. Re-attach checklist
Nothing to attach: all Diagnostics files are archived in `research/uploads/2026-10-14/` in the repo. Tj's newest file (after v0.99.16) is the thing to wait for.

---
<sub>Audit: 13/13 sections · 13 rules · 5 corrections · 45 data points · secrets removed: none found · generated by active-memory</sub>
