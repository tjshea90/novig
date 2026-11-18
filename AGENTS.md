# AGENTS.md

<!-- handoff:learnings:begin -->
## Session Learnings (auto-updated by handoff)

### Implicit Rules
- Branches: cloud-config (settings.json), cloud-feed (feed.json.gz+status.json), phone-data (phone/bets, phone/study)
- Repo secrets: SGO_API_KEY, ODDSPAPI_API_KEY, ODDSPAPI_FREE_API_KEY, UNABATED_API_KEY, PINNWIRE_API_KEY, PINNAPI_API_KEY, PROPLINE_API_KEY, ODDS_API_KEY, PARLAY_API_KEY
- Repo CLAUDE.md: ckpt after each step, ship.sh then release.yml then record-release.sh, no subagent fan-out, never commit keys

### Key Decisions
- Decision: hand-written JSON codec CloudCodec, refEvent dropped — Reason: engine module lacks serialization plugin, refEvent heavy
- Decision: phone scan = CloudScanner behind SwitchingScanner picked by settings.cloudScan — Reason: Feed, sheets, auto-bet work unchanged
- Decision: cloudScan/cloudSave in ApiGates — Reason: ScanSettings at 255 JVM arg-slot limit (ScanSettingsSizeTest)
- Decision: cloud-feed one force-pushed commit, publish gap 10 s, poll 5 s with ETag — Reason: GitHub rate limits, branch never grows
- Decision: phone-data never rewritten via BetArchive channel github — Reason: permanent history for Claude
- Decision: Pinnodds/BoltOdds/Kalshi keys stay on phone; bid desk + live lane keep own scans — Reason: one-connection sockets, trading keys, cloud list is +EV rows only
- Decision: engine idles when cloud-config on=false, job ends after 10 min; handover at ~5h40m via self-dispatch — Reason: 6 h job cap, save minutes

<!-- handoff:learnings:end -->
