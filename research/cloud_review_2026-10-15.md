# Cloud scan review, 2026-10-15 (Tj: "fix anything that needs to be fixed, run full tests protocol, simplify ..., mirror settings, refresh always scans, freshness at a glance")

Read first: the live state (cloud-feed `status.json`, cloud-config, phone-data), then every cloud file end to end. What the live state showed (Tj's phone had been running v0.101.0 for ~1.5 h):

| Fact | Evidence |
|---|---|
| phone-data works | bets/bids day files and study days landed on `phone-data` every 15 min |
| the engine has 13 feeds from the app-sent keys | status `sources` |
| a cold first scan took 880 s at Tj's settings (8 leagues, props, 10 days); the next was at 1,213 of 8,152 Novig prices after 860 s, about 2 a second | status `progress*`, `lastScan*` |
| list ages at publication: Novig prices median 11 min, fair odds median 23 min | status `novigAge*`, `fairAge*` |
| Tj has **bids on (automatic, Volume mode) and the auto-bet on** with Scan on GitHub on | cloud-config settings: `maker` true, `autoBet` true |

## Defects found, and what was done

1. **Bids were starved in cloud mode (real money path).** The phone's "scan" in cloud mode returned GitHub's list, which holds only +EV bets; a +EV row is already cheaper to take than to bid on, so the bid desk (needs every priced line) made no bid from it. Fix: `ScanSettings.bidsNeedFullScan` / `cloudDrives`: with Vigilant-priced bids on (automatic or Recommend, the app's default) the phone scans itself and the engine is told to stand down (`on = false`), with the reason on the Cloud page and the Feed. Test: `CloudScanKeyTest`, `CloudScannerTest`, `CloudUiTest`. *Not fixed, by design:* using GitHub for bids needs every priced line in the list (TASKS TCL9c).
2. **The list could not keep itself honest in cloud mode.** The Feed's 15 s recheck and the Recheck button call the phone scanner's `recheck`, which needs a scan plan the phone no longer has: they did nothing. Fix: in cloud mode `MainViewModel.recheck` re-prices the listed rows from Novig's newest books over the phone's own connection (`requoteListed`) keeping GitHub's fair, and does it once at once after every new list (so a price read a moment ago never goes back to an older one).
3. **Pause and the kill switch did not stop the engine.** The engine kept scanning (and spending API credits) while scanning was paused. Fix: `cloudDrives` includes `!paused` (hand or kill switch); the engine stands down; resuming starts it.
4. **A removed token left the engine running.** Nothing could tell it to stop. Fix: `removeToken` writes `on=false` first, and removing the token switches Scan on GitHub off (the phone would otherwise show no scan at all).
5. **"Which settings reach GitHub" was a guess.** The whole `ScanSettings` is written on every change; the engine restarted a scan for any scan-affecting field, and for a few phone-only ones. Fix: `CloudScanKey` names every setting (and every member of `gates`, `vigGuards`, `guards`) as FETCH (restart), PRICING (next scan), FILTER (the very next list) or PHONE (ignored); `CloudScanKeyTest` fails for a new setting nobody classified. The engine also says which revision it priced under and whether a change is waiting for its next scan (`pricedConfigRev`, `settingsPending`), and the Feed shows "GitHub applies your latest settings from its next scan" when so.
6. **Freshness was not visible at a glance.** Fix: a chip in the Feed title (`CloudFreshness`): median and oldest age of the Novig prices SHOWN and of the oldest book behind each shown fair line, coloured (green: Novig median at most 90 s and oldest at most 5 min, fair median at most 10 min; amber; red: Novig oldest over 10 min, engine silent or off). Tap for how it was worked out. The numbers come from each row's own stamps (`bookFetchedAtMs`, `fairAsOfMs`), never a claim.
7. **The engine's own account of itself was invisible.** Fix: `status.scanNote` (Novig reads, unchanged, cached, left out as too late, throttling, each feed's fetched/reused/error) and `engine-log.txt` (last 200 lines) published with the list on `cloud-feed`: readable from the repository, no job log needed.
8. **Config polling was heavier than needed.** Every 3 s a `git fetch`; now an `ls-remote` first and a fetch only when the branch moved. The settings are compared only when the phone wrote something new.
9. **Actions clutter and a race.** The 15-minute schedule queued a pending run behind a running engine and the next schedule cancelled it (a cancelled run every 15 minutes). Fix: a `gate` job with no concurrency group ends the schedule's run unless the switch is on AND no engine is running; the engine job holds the concurrency group.
10. **Settings page** reordered into Token / Scan / API keys / History, the duplicate "Test"/status lines folded, the paused reason and settings-sync line added, a search entry for "Send my API keys to GitHub".

## What cannot be fixed from the app (measured)

The engine reads Novig through the PUBLIC routes (no key): about 4 a second allowed and about 2 a second seen from a GitHub runner (a shared address), against 14 a second for the phone's `trading::read` key. That is why a cold scan at these settings takes 15 minutes (the phone's own took about 8) and why fair odds go stale during it. The remedy is a read-only Novig key for the engine (`trading::read`: it cannot trade or move money), minted from the phone's management key and stored as a secret like the other keys. It changes Tj's Novig account, so it waits for his word (TASKS TCL11).
