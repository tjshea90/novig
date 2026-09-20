# RESEARCH — positive-EV betting on Novig

Living research file. This is where findings about the app's actual purpose
(finding +EV opportunities on Novig, at $0–$30/mo instead of OddsJam's
$199.99/mo Gold plan) get recorded permanently, the way `BRIEF.md` records
platform decisions. Append to this file as research continues — don't
overwrite prior findings, since a later session needs to see what an earlier
one already ruled out and why.

**Status as of 2026-09-20:** first research pass, done in response to Tj's
request (see `TASKS.md` / `INBOX.md`, 2026-09-20T04:36:03Z), plus a second
pass (2026-09-20T05:01:28Z) deep-diving Odds Assist Pro specifically (§8).
Nothing in this file has been built yet. Several load-bearing facts below
(marked **UNVERIFIED**) come from third-party docs/blog summaries, not
hands-on testing against Novig's real API — confirm them before
architecture is locked in. §8's Odds Assist Pro findings, by contrast,
**are** hands-on verified (live browser session against the real site,
2026-09-20) rather than secondhand — noted there explicitly.

---

## 1. Bottom line

**Updated 2026-09-20, second research pass — this is less optimistic than
the first pass below and should be read first.** Novig does publish an
official developer API (REST/WebSocket/GraphQL, §4.1) — but §4.1.1's
research strongly suggests it's built and gated for **institutional market
makers, liquidity providers, and B2B partners**, not individual retail
developers, and is plausibly how Novig actually gets paid (institutions
fund the commission-free retail product). There's no public signup form,
no published pricing, and Novig's own Developer Relations job posting
describes "high-touch," relationship-based onboarding, not self-serve.
Going straight to Novig's API may still be the cheapest path *if* Tj asks
and it turns out to be reachable — but plan for the realistic case that it
isn't, rather than treating it as the default. **SharpAPI's free
60-second-delayed tier is now the most realistic $0 path to real (if not
sub-second) Novig data**, with paid resellers ($79+/mo) as the fallback if
delayed data isn't good enough. The math/architecture below (§5-§7) is
unaffected either way — only which data source actually supplies the
Novig leg changes.

*(Original framing from the first research pass, kept for context — the
core insight, that a direct-from-Novig data source beats paying a
reseller's markup, is still right in principle; §4.1.1 above is what
changed is how *reachable* that direct source actually is):* A
free-or-near-free, better-than-OddsJam +EV tool **for Novig specifically**
looked achievable, for one reason that most "OddsJam alternative" writeups
never consider because they're built for generic multi-book arbing: **Novig
publishes its own official developer API** — REST, WebSocket, and GraphQL,
OAuth 2.0, sub-second live order-book data — built explicitly for
"developers and quantitative traders to automate strategies." That is the
single biggest cost lever available: every third-party odds aggregator
(SharpAPI, OpticOdds, Betstamp, SportsGameOdds, odds-api.io) that resells
Novig data is, per their own pages, pulling from this same surface (or
scraping the public site) and marking it up to $79–$399/mo. Going straight
to Novig's own API instead of through a reseller would be the difference
between "under $30/mo" and "free," **if** API credentials turn out to be
free to obtain — now looking unlikely for an individual, see §4.1.1.

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

### 4.1.1 Second research pass, 2026-09-20T19:45:42Z: pricing/access model — not encouraging

Tj asked directly: is the NBX API free, and how do you actually get it?
This pass found real, converging evidence (not proof — Novig has never
published pricing publicly) that the honest answer is **probably not free
for an individual retail trader, and probably not a quick self-serve
signup**:

- **Novig's own Developer Relations Engineer job posting** (the role that
  owns onboarding new API users) describes the API's target users as
  "market makers and liquidity providers, trading firms, and B2B or
  embedded partners" — an institutional/B2B list, retail traders not
  mentioned. The role is described as providing "detailed, high-touch
  support" to partners integrating the API — relationship-managed
  onboarding, not a self-serve dashboard. No pricing, rate limits, or
  formal signup process appears anywhere in the posting.
  ([Dreamwork listing](https://www.dreamworkhq.com/job/aa4d9582-c6f9-4c36-83ab-3e19d76ac7b0))
- **Novig's actual business model explains why:** multiple funding-round
  writeups describe Novig as charging *institutional market makers and
  liquidity providers* for access to retail order flow — that fee is
  explicitly what funds commission-free trading for retail users. API
  access to the live book is the product institutions pay for; giving it
  away free to individual retail traders would work against the platform's
  own monetization model, not just be an oversight in the docs.
- **There is a formal, gated "Market Maker" program**, separate from
  simple account signup: per Novig's CFTC exchange-rule filings, an
  individual, group, or corporation must be **approved** and **sign an
  agreement** with the exchange before being allowed to provide liquidity
  across markets — this is the kind of relationship the API is built for,
  not a form anyone fills out.
- **No public waitlist, application form, or self-serve API-key dashboard**
  was found anywhere via web search — not on novig.com, not in the help
  center (support.novig.us), not in press coverage. Every third-party
  reseller that already has Novig data (SharpAPI, OpticOdds, Betstamp,
  etc.) is, per the business-model finding above, most plausibly itself
  one of these paying institutional/data-partner customers — i.e., they
  already did the relationship-based onboarding Tj would need to do, at a
  scale that supports reselling it.

**Correction, same day (2026-09-20T19:49:13Z) — Tj found the real process,
which this session's web search never surfaced:** Novig's own in-app/site
support widget states plainly: *"API access is handled by our developer
team. Send an email to **developers@novig.com**. Include: who you are (and
your company/product, if applicable), what you're building, what data or
functionality you want, expected scale or usage. They review requests and
guide next steps from there."* So there **is** a real, concrete, always-
available path in — a real email address, a real intake process — it's
just not indexed anywhere web search reaches (likely gated behind the
in-app help widget rather than a public page). This doesn't resolve
whether individual/personal requests actually get approved or what it
costs — that's still unknown until Novig replies — but it fully answers
"what's the actual process": **email developers@novig.com** with those
four things. Tj sent that email 2026-09-20 (see the email itself, drafted
this session — not stored in this repo, it's a one-off communication, not
project documentation).

**Revised recommendation:** the email is sent — next step is simply
waiting for Novig's reply and updating this file once there's a real
answer (free vs. paid, approved vs. not, any conditions). Until then, plan
around the same fallback as before: **SharpAPI's free, 60-second-delayed
tier** is the most realistic $0 path to *some* real Novig data, with paid
resellers ($79+/mo) as the next fallback if delayed data isn't good
enough.

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
| **Odds Assist Pro** | **Yes, confirmed hands-on — see §8.1** | **Free** (no card; a free account unlocks more rows) | 30s refresh (claimed, unverified), undisclosed devig method. Real, live, currently-active Novig edges confirmed by direct testing 2026-09-20 — see §8.1 for the verdict on whether it's "as good as OddsJam." |
| **Sharp Lines** | Free tier: DraftKings + FanDuel only, **no Novig** | Free (limited) / paid unclear | Free tier capped at 2% EV, 60s delay — not a Novig source. |
| **AVO** | 70+ books claimed, Novig unconfirmed | Free "Explorer" tier (up to 2% edges) + paid | Worth checking directly for explicit Novig support. |
| **RebelBetting** | Unconfirmed | $99+/mo | Not price-competitive with the $30/mo ceiling; deprioritize. |

### 8.1 Odds Assist Pro deep-dive (2026-09-20, hands-on verified)

Tj asked specifically: *does it actually find +EV on Novig, and is it as
good as OddsJam?* Answered directly rather than restated from marketing
copy — this required actually loading the live tool in a browser
(pre-installed Chromium via Playwright) and reading real, current output,
not just reading vendor pages about it.

**Who runs it:** Upper 9 Media LLC (`oddsassist.com`, contact
`hello@oddsassist.com`) — a small operation ("small team," "passionate
sports fans" per their own about copy), not a funded/VC-backed company the
way OddsJam is. Public review evidence is thin: exactly **one** Trustpilot
review as of this research (5 stars, praises accuracy and says the
reviewer "became a profitable bettor" using it for over a year) — a single
data point is not a track record, and should be weighted as such.

**Does it actually find +EV on Novig? Yes — confirmed live, not just
claimed.** Loaded `pro.oddsassist.com/advantages/plus-ev` directly (no
account, no signup), opened the "Sportsbooks" filter, and isolated the
book list down to **Novig only** (deselecting the other 10 available-in-
California books one at a time — the filter UI requires this, there's no
single-click "Novig only" shortcut). With only Novig selected, the tool
surfaced real, dated, currently-live opportunities, e.g. (captured
2026-09-20, California region — results are state-filtered and will differ
by state):

| Event | Bet | Novig price (Am. odds) | No-Vig reference | Claimed edge |
|---|---|---|---|---|
| Miami Dolphins @ SF 49ers, 09/20 8:25 PM | Miami Dolphins ML | +809 | +666 | 18.73% |
| New York Liberty @ Toronto Tempo, 09/20 7:00 PM | Toronto Tempo ML | +733 | +643 | 12.22% |
| South Carolina @ Alabama, 09/26 11:00 PM | South Carolina ML | +400 | +349 | 11.47% |

This is real: current games, a working Novig-specific filter, a
distinct Novig logo tag on each row, live-updating numbers across repeated
loads. **So yes — it works, and it's free.** Confirms/upgrades §10's old
item 4 from "should trial" to "trialed, functional, positive result."

**But read the edge numbers with real skepticism before trusting them —
this is the substantive finding, not just "it works":** an 18.73% edge on
a +809 (extreme-underdog) moneyline is a *huge* number — multiples of what
a mature market like OddsJam's typically flags (real +EV edges are usually
low single digits to maybe 5–8%; anything materially higher on a stale or
thin line is a yellow flag, not a green one). §5 of this file already
covers *why* that's suspicious: **multiplicative devigging — the simplest
and most common method — specifically overstates underdog value**, exactly
the favorite-longshot bias problem. Odds Assist doesn't disclose which
devig method feeds its "No Vig Odds" column or which book(s) it references
(no "source of truth" picker the way OddsJam has, per §5's close). A large
apparent edge concentrated on extreme-longshot lines is at least partly
consistent with "simple devig method run on a thin, longshot-heavy
market," not necessarily "real, capturable mispricing." This doesn't mean
the tool is fake or the numbers are wrong — it means **the edge number by
itself isn't trustworthy without knowing the methodology**, which is
exactly the kind of thing a from-scratch app can do better by *disclosing*
its devig method and letting it be tuned (§5's recommendation already
independent of this finding, now with a concrete reason why it matters).

**Is it as good as OddsJam? No — but "as good" is the wrong frame for what
it actually is.** Feature-by-feature, from what's directly observable:

- **Coverage:** Odds Assist's own filter list shows ~12 books available in
  California (BetOnline, BetUS, Bovada, Fliff, Kutt, MyBookie, Novig,
  OG.com, Polymarket, ProphetX, Underdog, Kalshi) plus a longer
  "unavailable in your state" list (BetMGM, Caesars, DraftKings, ESPN Bet,
  Fanatics, FanDuel, Hard Rock, Pinnacle, BetParx, BetRivers, Bally Bet) —
  book availability is **state-gated** on this tool, worth remembering when
  judging results (a different state, Tj's actual one, may unlock more).
  OddsJam scans 50+ books with no such regional gate on the tool itself
  (regional legality is the sportsbook's problem, not the scanner's).
- **Transparency:** OddsJam lets the user pick the devig method and
  "source of truth" book; Odds Assist Pro discloses neither — a real gap
  for a serious bettor who wants to trust the number, not just the flag.
- **Free-tier row cap:** without an account, only ~3–4 rows are unblurred
  at a time ("Sign up for free to see more positive ev bets" gates the
  rest) — signup is stated to be a **free** account (no card mentioned),
  not a paid unlock, but this wasn't verified by actually creating an
  account (didn't create one on Tj's behalf without asking first).
- **Depth:** the product has Arbitrage Bets, Live Arb Bets, Low Hold Bets,
  Middles, and Moving Lines as separate free tools in its nav — broader
  than a bare EV scanner — but no visible bet tracker or CLV tracking on
  the pages checked, which OddsJam has natively.
- **Novig sits in the free "SPORTS BETTING" section of the product, not
  the paid "Prediction Markets Pro" ($19.99/mo) tier** — that paid tier is
  a separate product for Kalshi/Polymarket-style event contracts (its own
  nav items are "Earnings Mentions," "Politics Mentions," "Entertainment
  Mentions" — corporate-earnings and politics contracts, nothing
  sports-related). This resolves an apparent contradiction from the first
  research pass (an Odds Assist review page said Novig is "missing an API
  for traders" — that page reads as stale/uninformed marketing copy about
  Novig in general, not evidence against Odds Assist's own free +EV tool,
  which demonstrably works against Novig right now regardless of what that
  one review page claims about Novig's API situation).

**Verdict for Tj:** Odds Assist Pro is real, free, and does currently
surface live Novig +EV opportunities — worth running alongside anything
else while deciding what to build, and a legitimate reason to not rush
into building a Novig scanner from zero if the goal is just "see +EV
Novig bets today." It is not a full OddsJam replacement (undisclosed
methodology, no source-of-truth control, no bet tracking/CLV, capped free
rows, state-gated book list) — and it's exactly those specific gaps, not
"build something free," that should define what "equal to or better than
OddsJam" needs to mean for the app this repo is building: **methodology
transparency (disclosed, tunable devig — §5) and real bet
tracking/CLV are the two concrete features to prioritize over Odds Assist
Pro, not just matching its edge-list UI.**

**Action item:** before writing any app code, actually run Odds Assist
Pro for a few real days (Tj's own state, his own account once he decides
to sign up) to see what fraction of its flagged Novig edges are real vs.
longshot-devig noise — this is a much stronger design input than a single
research-session snapshot.

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
   (§4.1) — **narrowed, not resolved, 2026-09-20 (§4.1.1):** strong
   circumstantial evidence (Novig's own DevRel job posting, its
   institutional-market-maker-funded business model, a formal gated
   Market Maker approval program, no public signup form anywhere) that
   this is a B2B/institutional relationship, not a free individual
   developer signup. Still not confirmed either way with an actual
   real answer from Novig — that conversation hasn't happened yet. Next
   step unchanged: Tj emails Novig directly and asks, but with
   expectations now calibrated to "probably a sales conversation," not
   "probably a signup form someone forgot to link."
2. **Does SharpAPI's free tier's raw-odds set include Pinnacle** (§4.3) —
   determines whether the reference leg is genuinely $0/mo or needs The
   Odds API's $30/mo tier.
3. **Novig's parlay fee structure** (§3) — only "TBD" piece of the fee
   model; low priority since pre-game straight bets (fee-free) are the
   sensible first build target anyway.
4. ~~Hands-on trial of Odds Assist Pro against Novig~~ — **done, §8.1**:
   confirmed working and free, but with an important caveat (undisclosed
   devig method, large edges on longshot lines are suspect). New follow-up
   from this: **run it for real over several days** (Tj's own state/account)
   to see what fraction of flagged edges hold up, rather than trusting a
   single snapshot — this is now the higher-value open item than the old
   "does it even work" question was.
5. Full field-level REST/WebSocket response schemas (exact JSON shape of
   order book / market-by-event payloads) — need to actually request a
   token and hit the API (or read the OpenAPI 3.1 spec docs.novig.com
   offers) once §1 is resolved, rather than inferring from doc summaries.

None of the above blocks starting architecture/BRIEF.md decisions — they
block finishing them. Do not start writing app code from this file alone
per `TASKS.md`; architecture still needs Tj's sign-off once the open items
above are resolved enough to make real decisions.
