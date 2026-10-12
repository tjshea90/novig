# TAN4: settings audit (2026-10-12, v0.89.34 -> v0.89.35)

Method: `ScanSettings` has 188 constructor fields; a script (reads every `val`, greps the other main sources) found 3 never read outside the class
(`apiBetStake`, `cnoEnabled`, `autoScanMinutes`: migration remnants, kept so old saved files still load) and 1 read in one place. So the clutter is
not dead fields: it is WHERE settings live (20 rows on the Settings home) and a few settings that can disagree. Findings and decisions:

## Structure (what Tj sees)
| Problem | Decision |
| :- | :- |
| API setup is spread over 9 rows: "APIs: on/off, test, usage", "API usage & keys", "SportsGameOdds Pro", "OddsPapi", "Unabated (free)", "OddsPapi Free (v4)", plus keys for PinnWire, pinnapi, PropLine, ParlayAPI, The Odds API inside "Fair odds & sources", Pinnodds' key inside "Pinnodds live" | ONE row, **"APIs & keys"**. Each API on that page has its switch, Test, usage and a "Keys & options" button to its own page (children of APIs, not on the home list). Keys backup + usage charts are a child page of it. |
| "Fair odds & sources" holds both the fair-odds MATH (method, sharp books, devig, guards) and the API list (switches + keys + credit options) | The API list leaves; the page keeps the math, the sportsbook props rule and the reference books, with a link to APIs. |
| The same switch twice (Polymarket, Kalshi, PropLine, ParlayAPI, The Odds API, Pinnacle on BOTH Fair odds and APIs) | One home: APIs. |
| Live engines (Pinnodds live, Live bids) sit in "Betting & alerts" beside the settings that matter every day | New group **"Live games & research"**: Pinnodds live, Live bids, Research mode. |

## Contradictions fixed
1. **OddsPapi Free "requests a day" chips went to 250 a day on a plan of 250 a MONTH** (the v0.89.34 file: 220 used in a day; Runway WARN). Chips now Off/2/5/10 and the feed also spends at most an even share of what the month has left (`Op4Budget.mayBuyFair`).
2. **Games starting within vs Days ahead**: a "starts within 72 h" with Days ahead 2 was silently cut to 48 h (`ScanSettings.scanWindowHours`). Now the window is exactly what "Games starting within" says; Days ahead only sets how far "Any" looks.
3. **The API health check complained about things that cannot be fixed**: a FAIL for an OddsPapi (v5) key already removed, and "tracked more than once" for two separately filled live bids. Both fixed (`HealthChecks`).
4. "Test Kalshi at 3 requests a second": the test passed in Tj's file (1098 requests, none refused); a one-time experiment kept as a permanent switch. See below.

## Left alone on purpose
- Four "smallest edge" settings (Alerts, Auto-bet, +EV feed, CNO list): different lists, and `Shadowed` already warns when one hides another.
- Auto-bet and Bids advanced sections: restructured last release (v0.89.30), the 1-100 sliders own the rules.
- Fields only read by migrations (`cnoEnabled`, `autoScanMinutes`, `apiBetStake`).
