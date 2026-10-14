---
date: 2026-10-14T22:40:15.106Z
project: novig
session: c33c5a8b
schema_version: 1
next_steps_count: 5
has_blockers: true
keywords: cloud scan, github actions, vigilant, cloud-feed, phone-data, v0.100.0
headline: Goal: Vigilant (Android, tjshea90/novig) scans run on GitHub Actions runner; phone only sets settings + reads +EV list; 
---

# Session Handoff Snapshot
> **Generated:** 10/14/2026, 10:40:15 PM

## 🎯 High-Level Objective
* **Goal:** Goal: Vigilant (Android, tjshea90/novig) scans run on GitHub Actions runner; phone only sets settings + reads +EV list; bets/bids/history auto-saved to GitHub for Claude analysis. Intent: offload bandwidth/CPU/battery to GitHub without losing odds freshness; permanent data for Claude.

## 📌 Current State & Next Steps
* **Status:** Done: engine, phone link, history saving, CLAUDE.md rule, resume.sh CLOUD line, release. Remaining: Tj manual setup (secrets, token, switch); never met real phone; bids/live lane not cloud-capable yet.
* **Blocker:** None in code. Waiting on Tj manual setup. cloud-config test branch currently on=false rev 2.
* **Next Action:** Tj: add repo secrets, make fine-grained token (Contents+Actions rw), paste in Settings > Cloud scan, switch Scan on GitHub on

### Remaining Queue
- [ ] TCL6: read phone-data, cloud-feed status.json, Diagnostics Cloud scan section from Tj's first run
- [ ] Check CI result on main for commit aa9eb8f
- [ ] Tune pass gap; cache build for 6h handovers
- [ ] Build CloudReferenceSource so bids/auto-bet can use cloud fair

## 🛠️ Modified Files Delta
* data/.../cloud/*: new engine, codec, client, scanner, sources, files
* cloud/: new JVM module CloudMain + build.gradle.kts
* .github/workflows/cloud-scan.yml: new engine workflow
* app/CloudLink.kt: new; VigilantApp.kt: localScanner, SwitchingScanner, adoptCloud, cloud.start; MainViewModel.kt: cloud actions + Diagnostics section; SettingsScreen.kt: CLOUD page
* ScanSettings.kt: cloudScan/cloudSave via ApiGates; BetArchive.kt: pendingMirror(channel)
* CLAUDE.md, TASKS.md (TCL0-6), research/cloud_scan_2026-10-14.md, tools/resume.sh, settings.gradle.kts, app/build.gradle.kts 0.100.0/276

## 🚫 Failed Approaches (DO NOT RETRY)
* Approach: async scan inside coroutineScope → Result: failure cancelled parent despite try/catch → Lesson: use supervisorScope
* Approach: status computedAtMs = scan start → Result: phone never refetched partial/final lists → Lesson: use publish time
* Approach: curl GitHub API in bash loop to poll CI → Result: blocked by pre-bash hook → Lesson: poll via MCP or git ls-remote

## 🔑 Crucial Context & Implicit Rules
* Branches: cloud-config (settings.json), cloud-feed (feed.json.gz+status.json), phone-data (phone/bets, phone/study)
* Repo secrets: SGO_API_KEY, ODDSPAPI_API_KEY, ODDSPAPI_FREE_API_KEY, UNABATED_API_KEY, PINNWIRE_API_KEY, PINNAPI_API_KEY, PROPLINE_API_KEY, ODDS_API_KEY, PARLAY_API_KEY
* Repo CLAUDE.md: ckpt after each step, ship.sh then release.yml then record-release.sh, no subagent fan-out, never commit keys

## Key Decisions
- Decision: hand-written JSON codec CloudCodec, refEvent dropped — Reason: engine module lacks serialization plugin, refEvent heavy
- Decision: phone scan = CloudScanner behind SwitchingScanner picked by settings.cloudScan — Reason: Feed, sheets, auto-bet work unchanged
- Decision: cloudScan/cloudSave in ApiGates — Reason: ScanSettings at 255 JVM arg-slot limit (ScanSettingsSizeTest)
- Decision: cloud-feed one force-pushed commit, publish gap 10 s, poll 5 s with ETag — Reason: GitHub rate limits, branch never grows
- Decision: phone-data never rewritten via BetArchive channel github — Reason: permanent history for Claude
- Decision: Pinnodds/BoltOdds/Kalshi keys stay on phone; bid desk + live lane keep own scans — Reason: one-connection sockets, trading keys, cloud list is +EV rows only
- Decision: engine idles when cloud-config on=false, job ends after 10 min; handover at ~5h40m via self-dispatch — Reason: 6 h job cap, save minutes

## Summary
v0.100.0 "Cloud scan" built, released, recorded (code 276). Engine: data/cloud (CloudEngine, CloudSources, CloudCodec, CloudFiles, CloudClient, CloudScanner/SwitchingScanner), :cloud module CloudMain, .github/workflows/cloud-scan.yml. Phone: app/CloudLink.kt, Settings > Cloud scan page, Diagnostics Cloud scan section. Live test run 38646389958 on runner OK (SGO+Polymarket+Kalshi, ~10 bets, idle on on=false, archive to cloud-archive-2026-10-14 release). Full floor 3569 pass. CI on main for aa9eb8f not confirmed.

---
*A short hint surfaces on session start; full context loads only if your next prompt matches a keyword above, or via manual `/handoff-resume`. The same record is available as a sibling `.json` file for external tools.*