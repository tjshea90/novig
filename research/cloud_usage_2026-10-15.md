# API usage meters with the scan on GitHub (Tj, 2026-10-15)

Tj: "Make sure I can still see accurate api usage meters in the app on my phone." (TASKS.md TCL14)

## Audit (TCL14a): where each number came from, and what the GitHub scan did to it
| Meter | Source | With the scan on GitHub (before) |
|---|---|---|
| Settings › API usage rows (per key: used / allowance / left), the Feed's usage strip, Diagnostics › API usage, the health checks, Runway | the phone's ledger `UsageMeter` (`usage.json`): every call a client makes is recorded; a provider that sends credits-left headers sets `remaining` | **wrong**: the engine makes the calls, on a ledger of its own in the runner's `out/usage.json`; the phone's ledger only saw the few calls the phone made (taps, closing lines, grading). A key the engine had spent showed full. |
| "the API's own figure" (The Odds API, PropLine, SportsGameOdds, ParlayAPI, OddsPapi Free) | `ApiProbe.check` / `parlayAccount.refresh` on the phone with the phone's keys when the page opens or Refresh is tapped | **still right** (the provider's own total includes the engine's calls) but only as fresh as the last open of the page |
| PinnWire, pinnapi, OddsPapi v5, Unabated, BoltOdds (no usage prompt) | the phone's own count only | **wrong** (counted only the phone's calls) |
| Novig / Polymarket / Kalshi "requests today" | `countKeyless` on the phone | **wrong** (the engine's reads are ~all of them) |
| The engine's own rotation and pacing (which key, a day's share) | its own ledger, EMPTY at every 6-hour job start | a handover forgot what a daily allowance had spent |

## What was built (v0.105.0)
- **The engine publishes its ledger** (`usage.json` on `cloud-feed`, every 30 s, announced by `status.usageAtMs`): `CloudUsage.encode`. **The repository is public, so no key is in it**: each key becomes `keyId` (first 5 bytes of its SHA-256), call times are dropped, and any key text is scrubbed from a provider's note (`CloudUsageTest` asserts neither key, nor a fragment of one, is in the file).
- **The next job reads it back** (`UsageMeter.seed`, `CloudMain`): a handover keeps what a key spent today; an old period rolls over as usual.
- **The phone folds it in** (`CloudLink` loop 5 polls `usage.json` with an ETag every 30 s whenever a token is saved: a 304 is free; `AppContainer.cloudUsage`, `mergedUsage`, `CloudUsage.rekey` maps the ids to this phone's own keys, an id with no key here is dropped): `UiState.usage` is now the MERGED book, so the meters, the strip, Diagnostics, the health checks and Runway all see what both machines spent. `UsageMerge`: own counts add up; a provider's own figure (`KeyUsage.figureAtMs`, new) is the newest reading from EITHER machine and decides `remaining`/`used`; a rest set before a newer figure that shows credits left is lifted; keyless counters add.
- **Each meter says how much was GitHub's** ("of which the GitHub engine made N (its count as of 20 s ago)") and **how old the provider's figure is** ("620 left (the API's own figure, read 2 min ago)"); a counted figure says "counted: this phone's calls plus GitHub's". Diagnostics says the same per provider.
- **The provider-reported figures stay fresh**: with the scan on GitHub the usage page asks the APIs' own prompts again every 2 minutes while it is open (free), besides opening and Refresh. An API that is switched off is still never called.

## Limits to say plainly
- The two ledgers are not one: the phone's key rotation (`pick`) still decides on the phone's own ledger plus nothing from GitHub, so on a daily own-count API (PinnWire 100 a day) the phone could try a call the engine's spending already used up; the provider's 429 then rests the key (`recordDepleted`) as always. The engine's first answer from a metered API (credits-left headers) corrects its ledger for what the phone spent.
- The engine's count is as old as its last publish (30 s) plus the phone's poll (30 s); each meter says how old.
- Pinnodds and Kalshi's own key are phone-only, so their meters are the phone's alone.
