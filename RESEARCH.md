# RESEARCH — positive-EV betting on Novig

Living research file. This is where findings about the app's actual purpose
(finding +EV opportunities on Novig, at $0–$30/mo instead of OddsJam's
$199.99/mo Gold plan) get recorded permanently, the way `BRIEF.md` records
platform decisions. Append to this file as research continues — don't
overwrite prior findings, since a later session needs to see what an earlier
one already ruled out and why.

**Status as of 2026-09-20:** first research pass, done in response to Tj's
request (see `TASKS.md` / `INBOX.md`, 2026-09-20T04:36:03Z). Nothing in this
file has been built yet. Several load-bearing facts below (marked
**UNVERIFIED**) come from third-party docs/blog summaries, not hands-on
testing against Novig's real API — confirm them before architecture is
locked in.

---

## 1. Bottom line

A free-or-near-free, better-than-OddsJam +EV tool **for Novig specifically**
looks achievable, for one reason that most "OddsJam alternative" writeups
never consider because they're built for generic multi-book arbing: **Novig
publishes its own official developer API** — REST, WebSocket, and GraphQL,
OAuth 2.0, sub-second live order-book data — built explicitly for
"developers and quantitative traders to automate strategies." That is the
single biggest cost lever available: every third-party odds aggregator
(SharpAPI, OpticOdds, Betstamp, SportsGameOdds, odds-api.io) that resells
Novig data is, per their own pages, pulling from this same surface (or
scraping the public site) and marking it up to $79–$399/mo. Going straight
to Novig's own API instead of through a reseller is the difference between
"under $30/mo" and "free," **if** API credentials turn out to be free to
obtain (see §4.1 — this is the one fact most worth confirming next).

Recommended shape of the app, in one paragraph: pull Novig's own live board
directly from its WebSocket (`wss://api.novig.com/tape`) for the tradable
price — this is the leg that must be real-time, and Novig already pushes it
event-driven, so no polling is needed. Separately, pull a slower-moving
"what's this actually worth" reference (devigged consensus or a sharp book
like Pinnacle) from a cheap or free multi-book odds API, and devig it
ourselves rather than paying a provider's markup for a pre-computed
fair-odds field. Compare the two, net out Novig's own trading fee (only
charged on live/in-game and parlay taker fills — pre-game straight trades
are free on both sides, see §3), and surface anything with a real edge.
Nothing about this shape requires OddsJam-tier spend.

## 2. What "positive EV" means on Novig specifically (this is NOT the same
   question as on a traditional book)

Most +EV explainers (OddsJam included) assume the target book has a
built-in vig, and the "edge" is that one soft book hasn't repriced as fast
as a sharp reference like Pinnacle. **Novig doesn't work that way.** It's a
peer-to-peer exchange: users post prices (Make) or accept posted prices
(Take), Novig takes no cut on the price itself, and there's no bookmaker
margin baked into Novig's own board to strip out
([how it works](https://www.oddsshopper.com/articles/prediction-markets/how-does-novig-work),
[legalsportsreport.com](https://www.legalsportsreport.com/prediction-markets/novig-promo-code/)).

So the source of edge on Novig isn't "beat the house's stale vig" — it's
**"the crowd hasn't converged to true probability yet."** That happens
because Novig is newer and thinner than the retail-book market as a whole,
so a market can sit away from fair value until enough traders (or an arb
bot) correct it. This means the app's actual job is: compute a fair
probability from *outside* Novig (devigged consensus / sharp reference),
compare it to whatever price is currently sitting on Novig's book, and
flag the gap — the same comparison shape as traditional +EV tools, but the
"edge" being measured is convergence lag on a thin exchange, not a stale
vig on a soft book. Worth stating explicitly in the app's own docs/UI so a
future session (or Tj) doesn't quietly drift into building a devig-Novig's-
own-book tool, which would be measuring the wrong thing.

Novig quotes prices as **decimal probabilities**, not American/decimal
odds: a price of `0.524` means $0.524 staked to win $1.00 total
([data model](https://docs.novig.com/api-reference/data-model)). That
actually simplifies the EV math (§5) — no odds-format conversion needed,
Novig's own price *is* already an implied probability; the only conversion
work is on the external reference-odds leg.

## 3. Novig's fee structure (must be netted into EV, or the numbers lie)

Per [Novig's own fee docs, summarized via search](https://support.novig.com/en/articles/16195057-fees-on-novig)
and [oddsassist's explainer](https://oddsassist.com/prediction-markets/novig-fees/)
(**UNVERIFIED against Novig's page directly** — corroborate before shipping
the fee constant into the app):

- **Pre-game straight trades: $0 fee**, for both the Maker and the Taker.
- **Live (in-game) straight trades:** Taker pays `0.03 × P × (1 − P)` per
  $1 of contract, where `P` is the price in dollars (so ~$0.0075 per $1 at
  a 50¢ price — roughly 0.75% of stake at even money, less at extreme
  prices). Maker side is still $0.
- **Parlays:** fees apply; exact structure not yet pulled from source —
  TODO next research pass.
- **Maker Credit Program:** makers whose resting live orders get filled
  earn back 50% of the taker's fee as a credit.

Implication for the app: EV on **pre-game straight bets is fee-free** — the
simplest and highest-priority case to build first. EV on **live/parlay**
bets must subtract the taker fee from raw edge before calling something
+EV, or the app will surface false positives that look profitable but
aren't once the fee is paid.

## 4. Data sources compared

### 4.1 Novig's own official API — the key finding, needs confirmation

Full public docs at **docs.novig.com** (also indexed at
[docs.novig.com/llms.txt](https://docs.novig.com/llms.txt) for a fast
site-map read). Confirmed from the docs:

- **REST**, base URL `https://api.novig.com/nbx/v2` — market data, order
  book, positions, order management.
  ([rate limits page](https://docs.novig.com/api-reference/rest-api))
- **WebSocket**, `wss://api.novig.com/tape` — channels: `tape` (global
  order-book updates across all markets), `lifecycle` (market state:
  OPEN/END/CLOSE/START, EVENT_GOLIVE/EVENT_UNLIVE), `private` (your own
  order fills), and a per-market channel `{marketId}`. Server pings every
  15s; a missed pong drops the connection.
  ([WSS overview](https://docs.novig.com/api-reference/WSS/overview))
- **GraphQL** — ad hoc queries across markets/players/history, with a
  playground.
- **Auth:** OAuth 2.0. Docs state you "request your client ID and secret
  from Novig," then `POST https://api.novig.com/nbx/v1/auth/emm-token` to
  get an access token, which **expires every 30 minutes** (build token
  refresh in from day one, not as an afterthought).
- **Rate limits (generous — nowhere near a constraint for a single
  personal app):** standard endpoints 256 req/s, order placement 256/s,
  cancellation 512/s, event screener 1,024/s, batch/strike 64/s, kill
  switch 1 per 30s. 429 responses carry `Retry-After`/`X-RateLimit-*`
  headers in **milliseconds**, not seconds — easy off-by-1000 bug to watch
  for when implementing backoff.
- **REST endpoints relevant to a read-only scanner** (no trading needed to
  just watch the board): get market, get open markets, get order book, get
  markets by event, get markets by events **batch (up to 50 events at
  once)**, get valid tick levels.
- Separately, **data.novig.com** publishes free daily historical
  trade/market-snapshot data files (volume, open interest, price range) —
  useful for backtesting a devig/EV strategy against Novig's real
  historical convergence behavior, not useful for live scanning since it's
  daily-batch, not streaming.

**UNVERIFIED and the single most important thing to confirm before
committing to this architecture:** nothing in the docs states whether
requesting a client ID/secret is free, requires an existing funded Novig
trading account, or involves an approval process. Given the API is
explicitly marketed at "developers and quantitative traders" building
trading bots, and Tj already has a Novig account (he's currently betting
there via OddsJam), the likely path is: **email/contact Novig from the
account he already holds and ask for API access** — but this needs an
actual conversation with Novig, not an assumption. This is the top action
item, ranked above any of the code below.

### 4.2 Third-party resellers (fallback if 4.1 turns out gated/paid)

All of these sit on top of the same underlying Novig data (either the
official API or scraping the public site) and add a markup for
normalization across many books:

| Provider | Free tier | Cheapest paid tier w/ Novig real-time | Notes |
|---|---|---|---|
| **SharpAPI** | $0/mo, 12 req/min, **60s-delayed** raw odds (no fair-odds calc) | Hobby $79/mo — real-time + arb | Pro $229/mo adds +EV/middles/splits. Fair-odds/Pinnacle no-vig field is paid-only. |
| **odds-api.io** | Not detailed in free tier | — | Sub-second via WebSocket on paid; explicitly states it **scrapes** Novig's public site ("not affiliated with NoVig"), not the official API — same legal footing as any scraper, see §7. |
| **SportsGameOdds** | Free tier exists, limits unclear | $99–$499/mo | Object-counted billing (each returned item = 1 object). |
| **OpticOdds, Betstamp, MetaBet** | Not confirmed | Not confirmed — reputationally enterprise-tier | Not deep-dived this pass; revisit only if 4.1 is a dead end. |

**Conclusion: no third-party reseller gets real-time Novig data under
$30/mo.** The entire "under $30/mo and real-time" goal depends on §4.1
panning out. If Novig's API access turns out to require a funded/approved
account but is otherwise free, that's still a win. If it turns out to cost
real money to obtain, the fallback is SharpAPI's free 60s-delayed tier —
"real-time" in the request would then mean "as real-time as free gets,"
worth flagging to Tj rather than silently downgrading the target.

### 4.3 Reference-odds leg (fair-probability comparison, not Novig itself)

Doesn't need to be sub-second — line consensus moves far slower than an
individual exchange order book, so a 30–60s-delayed free tier is fine here:

- **The Odds API** — free tier: 500 credits/mo (~16 req/day at 1
  credit/call); paid $30/mo for 20,000 credits. Confirmed to include
  **Pinnacle** plus ~40 books (US: DraftKings/FanDuel/BetMGM/Caesars/
  Bovada/MyBookie; EU: 1xBet/Betfair/Unibet/etc.) — does **not** include
  Novig itself.
  ([the-odds-api.com](https://the-odds-api.com/))
- **SharpAPI free tier** ($0/mo, 12 req/min, 60s delay) — raw (non-devigged)
  odds across ~40 books including Novig. If Pinnacle is in that free
  coverage (**UNVERIFIED — need to check directly**, the search summary
  only confirmed Pinnacle-sourced fair odds are a *paid* SharpAPI feature,
  not whether raw Pinnacle odds are in the free raw-odds set), this alone
  could supply both legs' non-Novig reference data for $0/mo.
- Either option comfortably fits under $30/mo, and the free tiers plausibly
  fit under $0/mo — **do our own devigging** (§5) rather than paying for a
  provider's precomputed fair-odds field, since a provider's is a black box
  and the app should let the edge be inspected/tuned the way OddsJam lets
  users pick a "source of truth."

## 5. The math: devigging methods (with actual formulas)

Devigging = stripping a book's margin/overround out of quoted odds to
recover the market's implied "true" probability per outcome, so
probabilities sum to exactly 1 instead of >1
([betherosports.com overview](https://betherosports.com/blog/devigging-methods-explained)).
Using decimal odds `O_i` for outcome `i` of `n` outcomes in a market
(from [applied-probability-institute/no-vig-fair-odds](https://github.com/applied-probability-institute/no-vig-fair-odds),
**UNVERIFIED against a primary academic source — re-derive/sanity-check
before shipping any of these into a bet-sizing decision**):

- **Multiplicative (proportional):** the standard/simplest method, and
  what most books' own margin resembles.
  ```
  P_fair,i = (1/O_i) / Σ_k(1/O_k)
  ```
  Known bias: overstates dogs' true probability / understates favorites',
  because it spreads margin proportionally regardless of where the vig
  actually got added.

- **Additive:** spreads the overround evenly across outcomes instead of
  proportionally.
  ```
  P_fair,i = (1/O_i) − [Σ_k(1/O_k) − 1] / n
  ```
  Simpler, generally considered less accurate in practice than
  multiplicative or power.

- **Power method:** solves for a single exponent `k` such that the
  power-transformed implied probabilities sum to 1, then applies that `k`
  to each side. Corrects multiplicative's favorite/dog bias by assuming
  books skew more margin onto favorites (where public money concentrates).
  ```
  solve k such that: Σ_i (1/O_i)^k = 1
  P_fair,i = (1/O_i)^k
  ```
  Requires a numerical solver (bisection/Newton's method) for `k` — no
  closed form.

- **Shin's method:** models the overround as arising from a fraction `z`
  of "insider"/sharp money, and solves for `z` and the resulting fair
  probabilities simultaneously. Considered the most theoretically grounded
  for correcting favorite-longshot bias, at the cost of being iterative
  (no closed form) and more complex to implement correctly.
  ```
  let π̂_i = 1/O_i  (raw implied probabilities)
  solve for z such that:
    Σ_i [ sqrt(z² + 4(1−z)·π̂_i²/Σπ̂) − z ] / [2(1−z)]  =  1
  P_fair,i = [ sqrt(z² + 4(1−z)·π̂_i²/Σπ̂) − z ] / [2(1−z)]
  ```

Both OddsJam and Sharp Lines let the user pick which method (and which
book as "source of truth," e.g. Pinnacle) computes the reference fair
odds — worth doing the same rather than hard-coding one method, since
different methods disagree meaningfully on favorite-heavy lines and this
is exactly the kind of tunable-not-hardcoded decision a +EV bettor cares
about.

## 6. EV calculation, adapted for Novig's probability-price format

Since a Novig price `P_novig` for outcome `i` already *is* a probability
(§2), and a devigged reference gives a fair probability `P_fair,i` for the
same outcome from step 5:

```
raw_edge   = P_fair,i − P_novig        (positive = Novig is underpriced, i.e. +EV to buy)
EV_per_$1  = P_fair,i × 1 − P_novig    (expected profit per $1 of eventual $1 payout)
ROI        = EV_per_$1 / P_novig       (expected return on capital staked)
net_EV     = EV_per_$1 − fee(context)  (§3 — 0 for pre-game straight, taker-fee formula for live)
```

Flag `net_EV > 0` (with whatever confidence threshold Tj wants — OddsJam
users commonly filter to a minimum ROI% to account for reference-line
noise, not literally flag anything net-positive by a penny).

## 7. Real-time architecture on Android (Moto G 2026, battery-aware)

This ties directly into `BRIEF.md`'s "one cheap-tier phone" constraint and
the "Resource/battery waste" full-test checklist item in `CLAUDE.md` — get
this right from the start rather than retrofitting it:

- **Novig's own leg is naturally event-driven** (§4.1's WebSocket push) —
  this is the *good* case for battery: no polling loop needed for the
  price data that actually needs to be fresh, Novig pushes only on real
  changes.
- **Single persistent WebSocket connection**, not one per market/event —
  multiple simultaneous sockets meaningfully increases battery drain;
  subscribe to the `tape` channel (or targeted `{marketId}` channels for
  whatever's being watched) over one connection.
- **Foreground service with a persistent notification** while actively
  scanning — Android restricts background network access outside a
  foreground service or WorkManager, and a live scanner is squarely a
  "noticeable to the user" ongoing operation, which is also the honest UX
  answer (the user should be able to see the app is live-watching, not
  have it silently draining battery invisibly).
- **Respect Doze mode** — don't fight Android's background restrictions
  when the app is actually backgrounded/screen-off for a while. A
  reasonable default: full live WebSocket scanning only while the app is
  foregrounded or the persistent-notification service is explicitly on;
  fall back to Firebase Cloud Messaging (or just stop entirely) rather
  than trying to hold a wake lock indefinitely — the CLAUDE.md full-test
  protocol will explicitly check for exactly this kind of drain.
- **Exponential backoff with jitter on reconnect** — the WebSocket *will*
  drop (network changes, Doze, server restarts); reconnect logic needs to
  back off rather than hammer Novig's endpoint, both to be a good API
  citizen (relevant if credentials are tied to Tj's real account, §4.1)
  and because tight retry loops are themselves a battery/network drain.
- **The reference-odds leg (§4.3) should poll, not stream** — it doesn't
  need to be real-time, and polling on a longer interval (whatever the
  free/cheap tier's rate limit comfortably allows) is both cheaper on
  quota and lighter on battery than holding a second always-on connection
  open for data that isn't the time-critical leg.
- Recommended library note (not yet decided — belongs in `BRIEF.md`'s
  toolchain section once picked): OkHttp's WebSocket client is the common
  Android-native choice, with built-in listener callbacks and background
  dispatching, over hand-rolling socket management.

## 8. Competitive landscape — what we're actually trying to beat/match

For context on what "equal to or better than OddsJam" is actually
competing against, and to sanity-check the target isn't already solved by
an existing free tool covering Novig:

| Tool | Novig coverage | Price | Notes |
|---|---|---|---|
| **OddsJam** | Yes (per its own site) | Gold $199.99/mo (EV+arb+middles); Trends $19.99/mo; Platinum ~$400–500/mo | The incumbent being replaced. Lets users pick devig method + source-of-truth book. |
| **Odds Assist Pro** | **Yes, explicitly** — Novig odds feed into its EV calc | **Free** | 30s refresh, scans major US books + prediction markets including Novig, shows no-vig odds + ROI%, deep-links to bet slip. Worth a hands-on trial before building from scratch — this may already satisfy a chunk of the "free +EV on Novig" ask today, even if Tj still wants his own app long-term. |
| **Sharp Lines** | Free tier: DraftKings + FanDuel only, **no Novig** | Free (limited) / paid unclear | Free tier capped at 2% EV, 60s delay — not a Novig source. |
| **AVO** | 70+ books claimed, Novig unconfirmed | Free "Explorer" tier (up to 2% edges) + paid | Worth checking directly for explicit Novig support. |
| **RebelBetting** | Unconfirmed | $99+/mo | Not price-competitive with the $30/mo ceiling; deprioritize. |

**Action item:** before writing any app code, actually try Odds Assist
Pro's free tool against Novig for a few days — if it already does most of
what Tj needs for $0, the app's differentiator needs to be something Odds
Assist doesn't do (e.g. Novig-only focus with deeper order-book insight
that a generic multi-book tool wouldn't bother with, automated
bet-sizing/Kelly, a UI built for Tj's specific workflow, or trading
automation via Novig's REST order endpoints that a read-only scanner
wouldn't offer) rather than rebuilding an EV scanner that already exists
for free.

## 9. Legal / ToS posture

- **Novig's own API is the clean case** — it's built and marketed by Novig
  specifically for algo traders automating strategies against their
  exchange. Using it to power a personal scanner (or even automated
  trading) is the API's intended use, not a gray area. Novig is a
  CFTC-regulated Designated Contract Market operating in 47 states + DC
  ([CNBC](https://www.cnbc.com/2026/06/16/novig-wins-cftc-approval-as-competition-intensifies-in-sports-prediction-markets.html),
  [sportshandle.com](https://sportshandle.com/novig-launches-cftc-regulated-sports-prediction-market-in-47-states/)) —
  Nevada, Arizona, and Michigan are excluded; confirm the Moto G 2026's
  operating location isn't one of those three before assuming account
  access works.
- **The reference-odds leg** (§4.3, Pinnacle/consensus via a paid API like
  The Odds API) is licensed data via a paying customer relationship — also
  clean.
- **If forced onto a scraping-based free fallback** (e.g. odds-api.io's
  "we aggregate publicly displayed odds... not affiliated" model) for the
  reference leg specifically — this app would only ever use that data as
  an internal comparison signal, never republish/redistribute it, which is
  the same posture the entire +EV tool industry operates under. Still
  worth being aware this leg (if it ever comes to this) sits on less solid
  footing than Novig's own official, sanctioned API — another reason to
  prioritize confirming §4.1 first.

## 10. Open items — what's still unverified, ranked by how much they gate
    the architecture

1. **Can Tj actually get a Novig API client ID/secret, and is it free?**
   (§4.1) — the single biggest unknown; everything else is a fallback if
   this is a dead end. Next step: contact Novig directly (support/API
   inquiry) from Tj's existing account.
2. **Does SharpAPI's free tier's raw-odds set include Pinnacle** (§4.3) —
   determines whether the reference leg is genuinely $0/mo or needs The
   Odds API's $30/mo tier.
3. **Novig's parlay fee structure** (§3) — only "TBD" piece of the fee
   model; low priority since pre-game straight bets (fee-free) are the
   sensible first build target anyway.
4. **Hands-on trial of Odds Assist Pro against Novig** (§8) — changes
   what the app's differentiator needs to be, doesn't change whether to
   build it.
5. Full field-level REST/WebSocket response schemas (exact JSON shape of
   order book / market-by-event payloads) — need to actually request a
   token and hit the API (or read the OpenAPI 3.1 spec docs.novig.com
   offers) once §1 is resolved, rather than inferring from doc summaries.

None of the above blocks starting architecture/BRIEF.md decisions — they
block finishing them. Do not start writing app code from this file alone
per `TASKS.md`; architecture still needs Tj's sign-off once the open items
above are resolved enough to make real decisions.
