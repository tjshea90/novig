# Phone vs engine audit (Tj chat t1792712388108-24de, 2026-10-22 23:39 UTC)
Rule (Tj): with the GitHub engine driving, toggles on the phone are settings for the engine; the phone takes no socket and runs no auto-bet or bid of its own unless GitHub is switched off. Predicate: `ScanSettings.cloudDrives` (cloud scan on, not paused).

| Path | Before | Now |
| --- | --- | --- |
| BoltOdds, Kalshi, SX sockets | closed when cloudDrives && live on GitHub (`phoneSockets`) | unchanged |
| Novig market websocket (`novig.stream`) | open whenever its switch was on | closed while cloudDrives (reads by request; `VigilantApp.syncSgo/useConnection`) |
| Novig order websocket | warmed always | off while cloudDrives (hand bets go by REST) |
| Pinnodds/BoltOdds live lane for the Feed ("Include live games") | opened the phone's sockets even with live on GitHub | waits while `phoneLive` is false (`pinnTick`) |
| Vigilant auto-bet (`AutoBettor.runTennis`) | stood down only when engine trading != OFF | never places while cloudDrives |
| Vigilant bid desk (`MakerRunner.stopReason`) | stood down only while the engine desk was live | stands down whenever bids are on GitHub (`gates.cloudBids`), even if the engine is quiet |
| Kalshi scan | local fallback | none while cloudDrives (v1.0.51) |

Open (needs the engine): CH66b the Feed's live rows come only from the phone's live lane, so with the phone's lane off the Feed shows no live rows until the engine publishes them; CH66c the CrazyNinjaOdds auto-bet lane still places from the phone (the engine reads CNO but does not trade it); CH66d the phone's by-hand Bet sheet still reads Novig by REST at the tap (not a socket).
Revert: remove the `cloudDrives`/`bidsFromCloud && cloudBids` conditions named above.
