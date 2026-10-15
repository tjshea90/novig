# A Vigilant that runs by itself on GitHub: what it takes, what limits it, in what order (Tj, 2026-10-15, TASKS.md TCL19)

Tj asked for: three Claude accounts that coordinate; 24/7 scanning for +EV bets and bids on GitHub; his Novig and BoltOdds keys on GitHub so GitHub places bets and bids itself over the sockets; a different runner when Novig refuses one; any of the three accounts shipping improvements one at a time; and a daily review that finds the most profitable bet and bid settings and changes them as the samples grow.

## What already exists (v0.106.1)
24/7 engine (6 h jobs that hand over to the next; a 15-minute schedule restarts one if none runs), BoltOdds sockets and a Novig READ key with the Novig stream on the runner, the measured API ranking, usage meters, the bid candidates, hot refresh, the daily GitHub review digest and the daily Claude review. The engine places NO order today: the phone's auto-bet and bid desk do.

## Done in this step
- **Three accounts, one worker** (`tools/coord.py`, PROTOCOL.md, 25 checks in `tools/test_coord.sh`, live on the `claude-coord` branch).
- **A refused runner address ends the job early and draws another runner** (`AddressWatch`, `End.ADDRESS`, the workflow's hand-over step): only inside a job's first 15 minutes, only for the ADDRESS verdicts (`ANONYMIZED_NETWORK`, `RESTRICTED_NETWORK_REGION`), at most 5 in a row, then one an hour. Novig's own documentation says a data-center address is fine; a runner that lands in a restricted state or on a list is an unlucky draw, and the next job draws again. The other check, the key holder's DEVICE, is never touched (below).

## The facts that decide how autonomous it can be (NOVIG_API.md §4, §11; docs.novig.com/api/errors)
1. **Novig runs two checks on a signed route.** (a) the request's internet address (restricted states, VPN/proxy/Tor lists; a data-center address is fine); (b) **the key holder's device must have geolocated from a permitted state within the last 3 days, by opening the Novig app.** After 3 days a PLACEMENT is refused (`451 GEOLOCATION_EXPIRED`; a read still passes). So orders from GitHub need Tj to open the Novig app on his phone at least every 3 days. That is Novig's compliance anchor, not an obstacle the engine may try to get round: when it happens the engine stops ordering, keeps scanning, and tells Tj (push). "Fully autonomous without me" therefore means "without me except opening the Novig app every few days".
2. **One Novig stream per key** (`stream` bucket 512 tokens at 4/s, 2,048 markets); the order socket is a second connection signed by the trading key with its own bucket. BoltOdds Pro: 3 connections; the engine uses 2.
3. **A GitHub secret can be read by any workflow that runs on main.** In a public repo whose main three Claude accounts push to, a trading key is exposed to anything those sessions (or something that talks them into it) commit. The only hard cap is what the key can lose.

## Safety model for GitHub placing orders (all of it required before a key is sent)
- **The key**: a `trading` key of the small Vigilant SUBACCOUNT only, funded with what Tj accepts to lose (its balance is the hard cap; a leaked or misused key can at worst lose that). Never the management key. Sent by the phone sealed to the repository's public key (as the read key is), so no Claude session ever sees it.
- **Switches only Tj flips (from the phone, never a Claude session):** `cloud trading: paper | live`. Default paper. The master switch (`cloud-config on:false`) still ends the engine.
- **Limits no shipped change may raise** (class C in research/review/ROUTINE.md): per bet, per game, wallet use, daily loss stop, number of orders a day, `RepeatGuard` bounds. They live in one `CloudLimits` object with a test that fails if a number goes up.
- **Never two orders for one decision**: an order carries a `clientId`; an unanswered order is looked up, never replayed (`NovigOrderSocket` rule). A restart or a hand-over between jobs must not repeat an order: the ledger of ids goes to `cloud-feed` and is read back.
- **Stand down, tell Tj**: `GEOLOCATION_*` (open the Novig app), 401/403/422/423, a daily loss stop reached, the wallet short, the engine's clock or feed too old: stop ORDERING, keep scanning, push to Tj.
- **Paper first.** The engine runs the whole order path with no key (a paper ledger of what it would have placed and the fills it would have got) for a week, and the numbers are compared with the phone's real bets before any live order. Tj decides the day it goes live.

## Order of work
| step | what | state |
| :- | :- | :- |
| TCL19a | three accounts, one worker | **done** |
| TCL19b | 24/7 chain audit; hand-over on a refused address | address hand-over **done**; chain audit and a "down for 20 minutes" alert next |
| TCL19c | engine decides bets and bids (auto-bet, bid desk, repeat guard, injuries, wallet from the read key) and keeps a PAPER ledger | next: the order-deciding loops (`AutoScanner.cycle`, `MakerRunner`, `AutoBettor`) are in the app module with Android pieces (Keystore, notifications, stores); the pure money logic (`MakerQuote`, `MakerDesk`, `MakerPlan`, `RepeatGuard`, `ApiBetPlacer`) is in `data`. A port of the loops into the `cloud` program is the real work |
| TCL19d | live orders over the Novig socket from GitHub | after a week of paper, the key, the switch, the limits |
| TCL19e | both sockets fully used on the runner | BoltOdds + Novig stream already held; orders socket with TCL19d |
| TCL19f | the improvement loop | gates drafted (ROUTINE.md class B), CHANGES.md, paper-shadow side waits for TCL19c |

## Needs Tj (see the chat answer)
1. The key and the limits: how much goes in the subaccount; the per-bet, per-game and daily-loss numbers; sending the trading key from the app after the paper week.
2. Confirming that he can open the Novig app at least every 3 days.
3. Permission for the unattended daily review to change betting and bidding rules through the written statistical gates (the permission layer in this session blocked the edit that would have granted it: it needs Tj's explicit word).
