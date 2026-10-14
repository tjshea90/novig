# Cloud scan: Vigilant's scans on GitHub runners (design, Tj 2026-10-14)

Tj: "Put vigilant scans on GitHub runners. Outsource as much Internet bandwidth and processing power as possible to GitHub without sacrificing quality or freshness of odds. Can this run on GitHub and simply stream me the bets list? I just control the settings from the phone?"

## What already exists (checked 2026-10-14)
- `Scanner`, `Pricing`, `ScanRunner`, every `ReferenceSource` client and the Novig public client are plain JVM code in `:data`. `lab/LabMain.kt` already runs the Scanner on a GitHub runner (`lab-record.yml`, 330 min, SGO key from a secret, public Novig client, no orders) and writes to the force-pushed `lab-data` branch.
- What is NOT in `:data`: the source wiring (`AppContainer.allReferenceSources/flatRateSources/baseReferenceSources`, `VigilantApp.kt` ~L2093-2186, built on the Android key store and `ApiGates`), and the feed filter (`UiState.feedOf`, `FeedBuild.kt`, app module). Both must move to (or be duplicated in) a JVM-visible place.

## Shape (recommended): cloud computes the list, phone re-prices at tap
1. `cloud-scan.yml` runs a long job (cron every 15 min as a backstop + self-dispatch ~5 h 50 min in, concurrency group so two never read APIs at once). `:cloud:run` loops: read `cloud/settings.json` (phone-written) -> build sources from env secrets -> `Scanner.scan` -> filter exactly as the phone's feed does -> write `feed.json.gz` (rows + fair, quote ages, scan time, version) -> publish.
2. Publish = force-pushed orphan commit on branch `cloud-feed` (no history growth; same trick as `lab-data`). Phone polls GitHub's Contents API with a fine-grained token and `If-None-Match` (304s are free), every 5-10 s. Latency runner->phone about 3-10 s, on top of the scan pass.
3. Phone: Settings > Cloud scan (switch). On = the phone's own REST scan and API fan-out sleep (bandwidth/CPU/battery to GitHub), the Feed shows the cloud rows with their age, and the Bet sheet still re-reads Novig's live price and the lowest fair at tap (existing behaviour), dropping a row that is no longer +EV. Settings changes are written to `cloud/settings.json` on `main`/`cloud-settings` via the Contents API; the runner picks them up on its next pass (a push to that file also wakes it via `repository_dispatch` if idle).
4. Step 2 (after the list works): a `CloudReferenceSource` so the phone's full pipeline (auto-bet, bids, guards, stars, history) can run on the cloud's merged fair lines with only Novig live on the phone. This keeps Novig's price as fresh as today and moves the 10-API fan-out off the phone.

## What stays on the phone (cannot or should not move)
- Real-money paths: auto-bet, the bid desk, bet placement (Novig trading key, location check, fills, `BetPriority`). 
- The 5-second live lane (Pinnodds + Novig sockets): a runner->GitHub->phone hop is 3-10 s, more than the 5 s rule. Live-only mode stays phone-side.
- Any API allowing ONE connection or holding a trading-capable key: Pinnodds socket, BoltOdds sockets (3 connections), Kalshi's signed key. The runner would evict the app's connection; keep them phone-side, or give the runner its own only if Tj says so.
- Novig's own stream (one connection per key): the runner uses the PUBLIC client (slower gate, 429s slow it down), so the cloud list's Novig prices are scan-fresh (tens of seconds), not stream-fresh. The tap-time re-read fixes the price; the list is for finding candidates.

## Costs, limits, risks (honest)
- Free: standard runners on a public repo have no minute cap; 4 vCPU/16 GB; 6 h per job; 20 concurrent jobs. Scheduled workflows can start late and are not guaranteed; a missed handoff = a gap of a few minutes (the phone shows the feed's age and falls back to its own scan after N minutes).
- GitHub's terms say Actions is for building/testing/deploying the project; a 24/7 scan feeding a betting tool is outside the usual use (the research lab already is). Risk: account/repo flagged. Tj should know and decide.
- The repo is public: the published list (bets, fair, books) is public; keys are not (secrets). Tj already accepts public bets and wallet figures; a continuous public +EV list is new.
- Publish rate: a force-push every 20-30 s is within normal Git limits but is the thing most likely to meet a secondary rate limit; fall back to 60 s.
- Keys: every API the cloud uses needs a repo secret (SGO_API_KEY exists). Tj adds them in repo Settings > Secrets (Claude cannot). Kalshi's private key stays out.
- Phone token: a fine-grained PAT (this repo only, contents read+write) sealed by the Keystore like other secrets, never logged.

## Build order
TCL1 move the source wiring + feed filter that a JVM needs into `:data` (tests prove the same sources/rows as the app for the same settings). TCL2 `:cloud` module + `cloud-scan.yml` (loop, settings.json, publish, handoff). TCL3 phone: Settings > Cloud scan, token, feed reader, Feed shows cloud rows with age, settings writer. TCL4 Bet sheet/age/fallback, Diagnostics CLOUD section. TCL5 `CloudReferenceSource` for auto-bet/bids. Ship each with `ship.sh`; nothing here has met a live key from this container.
