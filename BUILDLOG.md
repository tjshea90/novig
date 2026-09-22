# BUILDLOG — the durable record of every release GitHub has actually built

One line per shipped version, written ONLY by `tools/record-release.sh`,
ONLY after the GitHub Actions run for that version is confirmed green. Never
add a line by hand and never before the run is green — see `tools/record-release.sh`'s
own header for why, and `CLAUDE.md`'s "Releasing" section for the full flow
this file is part of.

This file is empty because nothing has been built yet — there is no Android
project scaffold, so there is nothing to release. The first line lands the
day that changes.

| tag | code | when | what changed |
|-----|------|------|---------------|
| v0.1.0 | code 1 | 2026-09-20T16:13Z | First beta release — sample-data devig/EV scanner, no live credentials wired in yet
| v0.2.0 | code 2 | 2026-09-20T20:53Z | Sport-selection picker — nothing loads until a sport is selected and refresh/pull-to-refresh is pressed. Live data wiring from the previous release (SharpAPI + The Odds API, multi-key auto-rotation, in-app Settings for keys) carries forward.
| v0.2.1 | code 3 | 2026-09-20T21:25Z | Fixed 'Scan failed' bug report: Settings 'Add a key' field now disables autocorrect/autocapitalize (was silently on by default, a real risk for a typed API key); KeyRotator's exhaustion error now names the actual HTTP status (429 vs 401) instead of a generic 'rate-limited or invalid' message, so the next failure is self-diagnosing.
| v0.3.0 | code 4 | 2026-09-22T05:24Z | Novig leg now uses NovigGraphQlClient — real, unauthenticated direct access to Novig's own internal GraphQL backend (gql.novig.us), verified against the actual novig-liquidity open-source package Tj supplied, not just its briefing. Opt-in via rotating proxies configured in Settings (empty = sample data, same as every provider); real ToS-gray-area risk disclosed in-app and in RESEARCH.md §4.4/§9. Parlay fee formula confirmed (0.10 multiplier). Dead SharpApiClient removed.
| v0.3.1 | code 5 | 2026-09-22T05:49Z | Added a free 'direct access, no proxy' opt-in for the Novig leg, answering Tj's question about free alternatives to paid proxies (VPN, airplane-mode IP cycling). NovigGraphQlClient now works with zero proxies configured, connecting directly over whatever network the device is routed through (a VPN active at the OS level works automatically). New Settings toggle, separate from the proxy list, with honest tradeoff copy.
