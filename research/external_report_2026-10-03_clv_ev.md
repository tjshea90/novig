# Beating CLV, Finding True +EV, and Avoiding Trap Bets
### A research-backed playbook for betting (and market-making) on Novig

**Compiled:** 2026-10-03 · **Purpose:** reference document for building a +EV betting app (taking and making bets/bids) and maximizing profit on the Novig prediction market.

**How to read this file:** The highest-confidence findings are up front. Everything below is sourced; the "Contested / unknown" section at the end marks what's opinion or unverified. Where a claim matters for real money, the source is named inline so you can check it yourself.

---

## 0. Executive summary — the ten things that matter most

1. **Beating the no-vig closing line is the best-validated predictor of long-run profit.** The close aggregates the most information and the sharpest money, so it is on average the most accurate price available. Joseph Buchdahl's ~20,000-bet Wisdom-of-the-Crowd record returned 3.4% profit on turnover vs 4.0% expected — close agreement, and CLV reaches statistical significance in ~50 bets where actual P/L needs thousands. (Buchdahl, via Pinnacle Odds Dropper blog, full page read)
2. **Always de-vig the closing line before computing CLV or EV.** Raw CLV overstates edge by roughly the book's margin (~4.5% at -110). Canonical example: bet CIN -6 -110, closes -6.5 -110 → raw CLV +3.34%, but vs the vig-free close the EV is **−1.32%**. (Unabated)
3. **Pinnacle's closing no-vig line is the professional reference price.** Pinnacle accommodates sharp action instead of limiting it and runs ~2% margins vs 4–5% at retail books. (Buchdahl)
4. **Small theoretical edges do not survive friction.** Practitioner consensus: pass below ~2% edge; most pros need 3–5%. A 1% theoretical edge is smaller than typical margin + de-vig error + slippage. (atswins.ai, ProfitDuel)
5. **Huge edges are a red flag, not a gift.** "Unusually large numbers should be checked carefully for stale odds, market mismatches or inaccurate probability estimates." A genuine +3% beats a fictional +10%. (ProfitDuel)
6. **Steam-chasing and RLM-following are negative-sum for the follower.** Only originators/first-takers and those who *switch sides* after a move profit; "players taking a number 1 to 1.5 points worse than the first takers are likely to lose in the long run." Some steam is deliberately manufactured to harvest followers. (SBR forum, citing bookmaker text; docsports)
7. **The real trap is adverse selection, not "trap lines."** A price that beats the market average but whose counterparty is informed will pass your EV screen and still lose. On exchanges, resting bids that beat Pinnacle's no-vig price are guilty until proven innocent. (market-microstructure literature: Glosten–Milgrom)
8. **Timing: closers beat openers.** NFL closing spreads picked the winner 65.9% vs 63.5% for openers (2007–2020). Bet early when you can anticipate movement; bet late when the information (lineups, weather) is worth more than the expected price decay. (PFF)
9. **Novig is now a CFTC-regulated prediction market (since Aug 4, 2026), not a commission sportsbook.** Pregame straight trades are fee-free; live taker fee = 0.03×P×(1−P) (max $0.0075/contract). Adverse selection applies *more* here because your counterparty is another user. (WSN review, full page read)
10. **Tailing sharps has no empirical support and strong theoretical problems.** Even elite pros run ~3–5% ROI; followers get a worse number; fake records and survivorship bias are rampant. The only legitimate use of sharp movement is as an input to update your fair price — never as a pick. (Rufus Peabody via SportsHandle; VSiN)

---

## 1. CLV: the master metric

### Definition
Closing line value = the difference between the price you took and the market's final (closing) price, measured against the sharpest book.

- **Moneylines (decimal odds, de-vigged close):** `CLV% = (your odds ÷ closing no-vig odds) − 1`. Positive = you beat the close.
- **Spreads/totals:** measured in points first (e.g., bet -2.5, closes -3 → +0.5 points of CLV), then converted to a percentage via implied probability. Unabated's method: `(P_bet − P_close) ÷ P_bet`; e.g., bet -195 (66.1%) vs close -220 (68.8%) → (68.8 − 66.1) ÷ 66.1 ≈ **4%** raw CLV.

### Why the close is the benchmark
Lines move on news and money; the close has absorbed the maximum of both, so it should on average be the most accurate price. For liquid markets (NFL, EPL) there is ample evidence the closing line approximates true probabilities on average. Pinnacle's close is the most accurate of any book because its business model accommodates rather than bans sharp bettors.

### Why CLV predicts profitability
If the closing no-vig price reflects the true price, beating it by X% means your expected value is X%. Empirically:
- Buchdahl's ~20,000-bet Wisdom-of-the-Crowd system: actual profit/turnover **3.4%** vs expected **4.0%** — within statistical significance.
- A second study: 952 Pinnacle-flagged +EV bets at recreational books; average price shortening **3.94%**; statistically impossible by chance (only 65 bets needed to prove it) — recreational books were forced to shorten as the EV was exploited.

### CLV vs actual P/L and sample size
- P/L moves in ±1-unit chunks (std dev ≈ 1.00); CLV moves in small increments (std dev ≈ 0.1). Consistent CLV reaches statistical significance in **~50 bets** vs **several thousand** for P/L.
- Don't seriously interpret a bettor's skill under **1,000 bets**; want p-value < 0.001 before getting interested. (Buchdahl, via sharpbetting.co.uk interview)

### Critical caveats
1. **Vig.** Must remove it from the closing line, or subtract it (~4.5% at -110, varies by bet type) from raw CLV. An SBR illustration: betting -104 a thousand times at a 1% margin loses 20 units even while closing -104 every time — you must beat the close by *more than the margin*.
2. **Pushes.** Key numbers matter. NFL 3 carries ~10% push probability; moving from -3 to -3.5 converts a push into a loss.
3. **Juice asymmetries.** Line moves can come through the price (-110 → -120) rather than the number; a half-point's value depends on landing probability (3 and 7 are the NFL key numbers). Compare full quotes and price the half-point before buying it.
4. **Absence of CLV ≠ absence of skill.** True odds *originators* (novel models unknown to Pinnacle) won't show CLV initially; but a line-shopping strategy that works *will* show CLV.
5. **CLV is a guidepost, not gospel** (Captain Jack Andrews, Unabated): in inefficient markets (props, WNBA, early-season college hoops) there is no reliable "true price" being discovered, so CLV is uninformative there. (Unabated, "Getting precise about CLV," full page read)

### De-vig methods
Equal-margin (splitting the overround evenly) is crude and inaccurate because it ignores favourite-longshot bias. Prefer Shin, odds-ratio, or logarithmic de-vig methods, which account for books shading longshots. Your app's fair-price engine should implement at least odds-ratio or Shin de-vigging on Pinnacle quotes.

---

## 2. Finding genuine +EV bets

### The professional standard: market-based, not model-based
A bet is +EV when the offered price exceeds the no-vig fair price: `EV = (P_win × profit) − (P_loss × stake)`.

**Market-based (the standard):** take the sharp market's no-vig price as the fair estimate — Pinnacle's close, or a curated blend like Unabated's "Unabated Line" (vig-free, blended from market-makers Bookmaker and Circa). Then line-shop: any retail book offering a better price than fair is +EV. The "Top-Down" workflow: e.g., true line +1 +100 → find +2.5 -110 at a soft book → edge **+2.53%**. (Unabated)

**Model-based (the honest evidence check):** build your own probability estimate (Elo, Poisson/Dixon–Coles, ML) and bet when the book's price beats your number. But a reproducible 2024–26 survey found *no* publicly documented model beats a sharp Pinnacle-style **closing** line — one Serie A structural model got a logarithmic-pooling weight of exactly 0.000 against the close; RF/XGBoost/SVM models all showed negative CLV vs Pinnacle. Academic work confirms forecasting accuracy and betting profitability are not monotonically related (Wunderlich, Garnica-Caparros & Memmert 2025). Practical upshot: **treat the sharp close as the ceiling; your model's value is as a fallback where closing odds are missing, not as an information add-on.**

### Edge thresholds — the consensus ladder
- **Pass below ~2%.** Below this, margin, de-vig error, and slippage eat you alive.
- **Standard action: 2.5–5%.** One widely used tier system: edge ≥4% + sharp alignment = max stakes; ≥2.5% = medium; ≥2% = speculative. (tommeng/sports-betting-claude edge-detection skill)
- **"Most pros will not even bother firing on a bet unless that edge is at least three to five percent."** (atswins.ai)
- **Huge edge (>5% vs sharp consensus) = investigate, don't celebrate.** First hypothesis must be *your* error: stale data, wrong market/line, settlement nuance, mismatched event. Verify event, market, line, reference price, settlement rules, and data freshness before betting. (ProfitDuel)

### The "true +EV vs noise" rule
An edge is only real if it is (a) computed against a de-vigged sharp reference, (b) survives a freshness check (<2h old reference, or re-checked inside 12h of start), (c) confirmed across books (is this price off-market vs *everyone*? If yes — why?), and (d) not within 30 minutes of unresolved material news. Line shopping improves the price on a position you've decided to take and lowers your break-even rate; it cannot create edge if your probability estimate is wrong. (mybookie.ag)

### Workflow tools (for reference)
OddsJam, Unabated Edge Tool, Pinnacle Odds Dropper (alerts when Pinnacle odds drop so you can bet the stale price at a slow book), ProfitDuel EV Matcher, Pikkit (bet tracking + CLV grading). (OddAlerts; ProfitDuel)

---

## 3. Sharp money: how it's spotted, and the limits of every signal

### The signals
- **Reverse line movement (RLM):** the line moves away from the side with the majority of tickets — e.g., 70% of bets on Team A at -6.5 but the line drops to -6. Action Network's formulation: compare opener vs current on bets with <50% of tickets; ideally <40% or <30%. (Sporting News; Action Network)
- **Tickets vs handle (money%) gap:** e.g., 35% of bets but 50% of the dollars = large (possibly sharp) wagers. (Action Network)
- **Steam moves:** sudden, near-simultaneous line shifts across many books, driven by syndicate money. Originators: Pinnacle, Circa, BookMaker, BetOnline, BetCRIS, Heritage. Followers (DraftKings, FanDuel, BetMGM, bet365) copy seconds later — a move seen only at a follower may already be repriced. (propprofessor-mcp research notes)
- **Line freeze:** heavy betting on one side but the line refuses to budge — the book fears giving the contrarian (sharp) side a better number. (VSiN)
- **Timing:** sharps bet openers (low limits, softest numbers) and return in the final pre-lock window when limits are highest; late moves are the most meaningful. (VSiN)
- **Services:** SportsInsights (pioneered RLM/ticket data), BetQL, Action Network, Unabated all sell bet-signal dashboards.

### Why you should not trust these signals as picks
1. **No published controlled study** was found quantifying how often RLM or ticket/handle signals actually beat the closing line or produce profit. The best available evidence is negative and practical: steam-followers get a number 1–1.5 points worse than originators and "are likely to lose in the long run" (SBR, citing a bookmaker text); "by the time you see a steam move, the value the sharps found is likely gone" (Sporting News).
2. **Splits are single-book samples** with unknown windows; "a percentage cannot establish a bettor's identity or skill" — news and whole-market moves explain many RLM prints. (mybookie.ag)
3. **Books shade lines to exploit square money.** Sportradar's "Alpha Odds" liability-aware pricing delivered **+11.84% profit uplift** and **+1.27pp margin** over 12 months to Aug 2026 across 81 books and 2.2bn tickets. Academic: "sportsbooks exploit public biases to maximize their profits" (Dmochowski et al., PLOS ONE 2023). Favourite-longshot bias literature shows books earn more on mismatches/longshots than tossups (UCD WP23_04). So a line that looks "shaded" may just be the book charging the public more — and a contrarian position against it is not automatically +EV.
4. **Steam can be manufactured.** Syndicates sometimes push a line one way precisely so steam-followers extend the move, then bet the other side harder — "a relatively common ploy." Many "steam" prints are just public one-sided action, not sharp money at all. (docsports; SBR forum)
5. **Books punish the behavior.** BetOnline's T&Cs explicitly reserve the right to deny bonuses over "Wise Guy" or "Steam" moves — chasing steam at square books is both -EV and account-threatening. (SBR forum)

### Bottom line on sharp signals
RLM / ticket-handle / steam are *questions to investigate* (what moved, when, and what price can I get *now*), not standalone picks. Only originator-confirmed moves, at the triggering number or better, have any theoretical basis — and retail bettors almost never get that number.

---

## 4. Trap bets and adverse selection

### Two different things share the "trap" label — keep them separate
- **(a) The folk "trap line":** a line that looks too good to squares but is correctly priced given non-public information (resting starters, weather). "If you see a really crazy line... Books probably know [something]. You are not getting a good deal." (SBR forum)
- **(b) The economically precise trap: adverse selection.** A price that beats the market average but whose counterparty is informed. This is the one that kills +EV bettors, because the bet *passes* the EV screen.

### How (b) happens mechanically
- **Stale lines at slow books:** books that adjust manually or during off-peak hours lag the market after news/steam. "The most straightforward way to locate stale odds would simply be to look for books (esp. 'recreational'-type books) with off-market lines... during off-peak hours following line movement at the major market players." (SBR forum) Double edge: bettors exploit stale lines, but books void them as "bad lines" and cut the limits of bettors who hunt them.
- **Exchange bids (Novig/Betfair):** a resting limit order that nobody has taken is, by Glosten–Milgrom logic, disproportionately likely to be mispriced *against the taker* — informed traders take liquidity when your quotes are wrong; as toxicity rises, rational makers widen, pull quotes, or exit. Prediction markets amplify this via un-hedgeable idiosyncratic outcomes and 0/1 gap risk on news. (Menaldo on market-maker behavior; CoinPaprika prediction-markets guide)
- **Operator market-making against you:** Crypto.com hired a quant sports trader for an internal market-making team tasked with "maximizing profits while mitigating risks" and imposed a 3-second delay on retail sports trades; Kalshi/Polymarket have similar internal desks. Your counterparty may be the house in disguise. (gamblerss.com)
- **News-timing:** a price that hasn't moved with the rest of the market around injury/lineup news is either stale (opportunity) or the book knows something you don't (trap) — you must determine which before betting.

### Trap-spotting checklist
1. **Cross-book comparison:** is the price off-market vs Pinnacle/Circa no-vig, or vs *everyone*? Off vs everyone = investigate, don't celebrate.
2. **Line-move chronology:** did the market move 10–30 minutes ago and this quote didn't follow? Check timestamps; stale quotes cluster in off-peak hours.
3. **Age of the quote/order:** a resting exchange bid that has sat untouched while the market moved is toxic until proven innocent.
4. **News/injury timing:** any team news in the last hour? If yes and the price didn't move, assume the counterparty knows.
5. **Limit behavior:** sudden limit cuts or delayed acceptance on that market = the book fears informed action there.
6. **Size available:** real value gets bet into; a "gift" with a suspiciously large amount available deserves extra skepticism.

### Pass rules for apparent +EV
- Edge >5% vs sharp consensus → first hypothesis is *your* error.
- Never take a stale-looking price in the 30 minutes after material news without confirming the news is fully priced.
- On exchanges, don't take resting bids in thin books; prefer posting your own price (Make) near fair value so *you* choose the terms.
- **If you can't explain why the counterparty is offering it, you are the liquidity being harvested.**

---

## 5. Timing analysis: when +EV appears, and when to strike

### The core tradeoff
Early = softer numbers, less information, more adverse-selection risk. Late = full information, efficient prices, little value. (mybookie.ag)

### Published data
- **Openers vs closers (NFL, 2007–2020):** closing spreads predicted the winner **65.9%** vs **63.5%** for openers. "Bettors looking to maximize performance should place their wagers as early as possible" — waiting for models/analysts eats value. (PFF) Counterpoint: opener prices often have reduced limits and move within minutes, so "beating the opener" on paper overstates achievable results. (SBR forum)
- **"Bet favorites early, underdogs late" (MLB moneylines, 2015–2018, n=9,813):** in the last 2 hours lines drift toward favorites (avg −3.4¢); underdogs ≥+180 at T-2 moved toward the favorite 54% of the time. VSiN states the same folk rule for NFL. (SBR forum; VSiN)
- **Line movement magnitude (NFL, 2,560 games, 2002–2011):** >80% of games moved ≤1 point from open to close; only 20% moved more than a point. (arXiv 1211.4000)
- **Late-window moves are tiny:** on modern prediction-market venues, the final two hours often move less than one tick (sports-edge-lab measurement) — late betting captures little.
- **Overnight/early-week:** NFL lines posted after Sunday Night Football "might not exist by Monday afternoon" as sharp money positions. (Unabated)
- **News windows:** the best soft-line windows are the first ~30 minutes after open, breaking injury news, and niche sports with thin coverage. (gamblingsite.com) If the whole market has a stale number on news (e.g., one book slow on an injury update while another moved), taking the stale side is the classic +EV play — but also the fastest way to get limited.
- **Late moves are the most meaningful but least capturable:** highest limits, pros betting last-second; the "line freeze" tell appears here. By then you are usually observing value, not capturing it. (VSiN)
- **Live:** books overreact to scoring runs (contrarian value on the other side); halftime brings wider menus and bigger limits; live vig is typically higher than pregame; your TV feed lags the book's data feed — never chase a play you saw on screen. (thespread.com)
- **Day-of-week / seasonal CLV effects:** no published data found. Treat any such claim as anecdote.

### Practical timing rules
1. Bet **early** when you expect the market to move against your position, or when you have genuine news speed. First ~30 minutes after open is the prime soft-number window.
2. Bet **late** when you need uncertainty resolved (injuries, weather, lineups) and the information is worth more than the expected price decay.
3. **Never bet during unresolved news chaos** unless you are the fastest.
4. On underdogs, prefer the late window (prices drift toward favorites); on favorites, prefer the early window.
5. The final 2 hours before start: observe for information, but expect little capturable value (moves are sub-tick).

---

## 6. Bet types and market selection

### Efficiency ranking (most → least efficient)
NFL/NBA main-line spreads → totals → moneylines → derivatives/alternatives → player props → niche-sport micro-markets. "The more exotic the bet, the more you pay."

### Typical hold by market
| Market | Typical hold |
|---|---|
| Spreads / totals | 4–5% |
| Moneylines | 3–6% |
| Player props | 6–12% |
| Futures | 15–30% |
| Same-game parlays | 15–25% (effective) |

Books keep headline markets keen and recover margin on props/parlays. Route each bet type to the operator that prices it best. (deucescracked)

### Player props — the documented soft spot
Books "offer hundreds, can't set them all perfectly." Props are often set from season averages/box scores without full context (usage, matchups); early prop lines are rough estimates; sharps "absolutely love prop markets" and hammer mispricings repeatedly — which is exactly why books impose much lower prop limits and limit winners fast. (gamblingsite.com; tommeng edge-detection skill)

Two warnings: (1) CLV is unreliable in props — "very few market-making books... very few market signals... sharp money is going to move the market more than it should, and that makes it less efficient" (Captain Jack Andrews, Unabated); (2) props are the scandal vector (the Rozier/Porter cases) — a reminder that some "inefficiencies" are someone else's inside information.

### Parlays — the math trap
Vig compounds per leg: two -110 legs price at +264; a coin-flip 2-teamer returns **−9% ROI**; three legs at +595 → **−13% ROI**. (ProfitDuel) Industry data: SGPs are 35–40% of operator GGR (up from <20% in 2021); average operator hold rose 6–7% → 9–11% (2021→2025) on prop-heavy parlay products; in NJ parlays are ~60% of online revenue. (gamblingsite.com)

The honest exceptions: a bettor with a *genuine* per-leg edge can earn higher ROI via parlays (SBR's 55%-per-leg example: 8% vs 5.05%), and **correlated** legs can flip -EV into +EV (books ban perfect correlation, but mispriced correlation — e.g., same-game combos priced at independence — is the real edge; see Miller/Davidow's synthetic-position theme).

### Odds boosts — usually traps, occasionally gifts
Boosts are marketing with $10–$50 max stakes. "Heavily boosted same-game parlays are usually structured to encourage adding more legs than you should; pre-built promotional parlays often bundle in a leg the book's own risk team doesn't like." (bettingpros.com) When a boost is genuinely +EV, apply it to the *longest* odds available: a 25% boost on +900 has EV +$22.50/$100 vs +$12.50 on an even-money play. (SBR forum)

### Futures
15–30% hold — "an expensive way to express an opinion unless you are getting a genuinely mispriced number." Hedging a live futures position is often correct. (deucescracked)

### Sports
The most documented edges: NFL (timing, key numbers, closing-line studies), NBA (pace/props), MLB (moneyline timing), European soccer (Wisdom-of-the-Crowd 20k-bet record; favourite-longshot and away-favourite biases; overreaction strategies — Wheatcroft 2020). Efficiency rises with liquidity and limit size; small markets are softer but can't absorb meaningful stakes.

---

## 7. Execution playbook

### Staking
Kelly: `Kelly% = (B×P − Q)/B`, where B = decimal odds − 1, P = your win probability, Q = 1−P. Full Kelly maximizes theoretical growth but guarantees brutal drawdowns (50%+ near-certain even with a real edge); **no serious bettor uses full Kelly.**
- Standard: **half-Kelly** (~75% of full-Kelly growth, far less variance) or **quarter-Kelly** (~44% growth, flat-bet-like variance) as the default.
- Never risk >1.5–2% of bankroll on one event.
- Without a tested model, flat 1–2% is safer than a guessed Kelly number.
- Kelly's built-in "don't bet" signal (zero/negative output) is itself valuable. (sportswagerblog; ibebet; betting.com)

### Line shopping
Hold 5+ books; check the sharp originator price (Pinnacle/Circa no-vig) first, then take the best retail quote; route each bet type to its best-priced operator. Even 1–2% price concessions compound into real money over rollover. (Peabody, via thelines.com)

### Record-keeping and CLV tracking
Log date, sport, market, book, odds taken, time placed, stake, and the sharp-book closing line (capture 5–10 min pre-game; **Pinnacle's close**, not your book's). Segment CLV by sport/league/bet type. Tools: Action Network, Pikkit, SharpSide (auto-tracking); OddsJam, Unabated, BettingPros (closing-line archives). **Track every bet, not just winners.** (sportsbettingoddscalculator)

### Avoiding limitation
Books limit winners: stake cuts ($1,000→$10), market restrictions, delayed acceptance, closure — legal in most jurisdictions; they cannot withhold legitimate winnings. (betherosports)
- Defenses: spread action across many books; bet liquid main markets; round stakes; avoid hammering steam at square books (books explicitly penalize "wise guy/steam" patterns); use exchanges/prediction markets (no house to beat); mix in low-hold/bonus-clearing action.
- Regulatory shift: Massachusetts (June 1, 2026) now forces books to notify within 48 hours with individualized reasons — operator data showed just 0.64% of accounts restricted, but winners disproportionately so. (SBR news)

### Hedging
Hedge futures when the locked-in value exceeds the position's risk-adjusted worth. Don't "buy back" to chase middles unless *both* sides pass the EV test (Unabated's MIN -7.5/-10 example: +6.81% vs −4.55% — the middle is not worth it).

### Pre-bet checklist (all must pass)
1. Edge ≥ 2% vs a de-vigged sharp reference (≥3% preferred)? If no → pass.
2. Reference price fresh (<2h old, or re-check inside 12h of start)?
3. Cross-book check: is this price off-market vs everyone, or just the best quote? If off vs everyone → find out why before betting.
4. Any news in the last hour not reflected here? If yes → wait or pass.
5. Lineup/weather confirmed (props, totals)?
6. Stake = fractional Kelly within 1.5–2% bankroll cap?
7. Will this bet get me limited at this book (steam pattern, tiny market)? If yes → smaller, rounder, or different book.
8. Settlement rules match my assumption (pushes, dead heats, cash-out terms)?
9. Recorded in the tracker with timestamp before placing?
10. Am I chasing, tilting, or "liking a team"? If yes → no bet.

---

## 8. Novig: taking vs making on a prediction market

### What Novig is now
Novig (founded 2021) began as a sweepstakes P2P sportsbook and flipped on **August 4, 2026** to a real-money **CFTC-regulated prediction market** (legal entity Ludlow Exchange, LLC; DCM approved June 16, 2026; cleared by Bitnomial). Sports-only; 21+; all US states except AZ, MI, NV; ~$125M notional volume in its first week post-relaunch. (Action Network; hellorookie.com; WSN — full page read)

### Mechanics
Full order book with two order types:
- **Take** (market order): instant fill at a posted price.
- **Make** (limit order): set your own price and stake; rounded to nearest tick; partial fills; sits in escrow until matched; editable/cancellable until matched.
Contracts priced $0–$1 = implied probability; winner settles at $1.00. Fully cash-collateralized (max loss = stake). Deep prop catalog; parlays and live trading supported. Cash Out button on eligible straight positions (not parlays/live props). (WSN)

### Fees (replaces the old "commission" idea)
- **Pregame straight trades: no maker or taker fee.**
- **Live straight trades:** makers free; takers pay **0.03×P×(1−P)** per contract (max $0.0075 at 50¢ — 100 contracts at $0.50 = $0.75).
- **Parlays:** 0.10×P×(1−P) coefficient baked into the quoted price (~3x pricier than straights per unit risk — avoid unless edge is large).
- **Makers earn a credit** = half the taker fee (~$0.0037/contract at even money); bot orders don't qualify.
- Bank/crypto deposits free; debit/Apple Pay 3%; withdrawals free, up to 3 days. (WSN; props.com)
- Marketing copy saying "no commission" is true only for pregame straights.

### EV math on Novig
Buying Yes at price P with true probability t → `EV = t − P` per contract, minus the taker fee if live (0.03×P×(1−P)) — at 50¢ that's 1.5% of notional, enough to erase thin edges. Pregame straights are pure t−P.

### How trap logic applies — harder here than at a sportsbook
Your counterparty is another user, and per prediction-market microstructure, resting quotes get picked off by informed flow; as toxicity rises, rational makers widen, pull quotes, or exit. **A resting bid that beats the Pinnacle no-vig price is guilty until proven innocent:** it may be stale (opportunity) or a sharp's trap (someone happy to sell you Yes at 40¢ because they know it's 30¢).

### Taking vs making: tactics
- **Taking** posted bids pays the taker fee (live) and accepts someone else's terms — only do it when the price beats your fair estimate by more than fee + uncertainty.
- **Making** (posting limit orders) earns the credit and lets you define the edge. The novice-friendly tactic: post near fair value on both sides in liquid markets (NFL sides/totals) and collect the credit — small-scale market-making.
- **Market-making discipline:** keep size small; cancel/reprice around news (lineups, injuries); never leave resting orders up through scheduled news events — that is exactly when informed takers strike.
- Anchor every market to Pinnacle no-vig close/fair before trading.
- Trade the deepest books first (NFL/MLB; far-out soccer books are thin — minimal volume a week out).
- Check the full order book on desktop before committing — depth tells you whether your size moves the price.
- Prefer Make orders to control entry rather than Taking into thin books.
- Use pregame straights for bonus/trade-credit play (fee-free; credits usable on $0.01–$0.60 contracts with 1x playthrough).
- Fund via bank/crypto, never cards (3% fee).
- No account-limitation risk on the exchange itself (you're not beating the house), but operator-affiliated market-makers may still be on the other side — treat fills as potentially informed.
- Watch the conflicts: Novig sponsors tout-adjacent content (e.g., Bet the Process sponsorship) — discount any "expert Novig picks" content accordingly.

---

## 9. Following sharp bets: the verdict

**What the services claim:** bet-signal dashboards (SportsInsights, BetQL, Action Network) and Discord/Telegram groups sell RLM alerts, ticket/handle splits, and "sharp" picks; public bettors post slips as proof. The implicit promise: trade on professionals' information without doing the work.

**Why it fails in practice:**
1. **You get a worse number than the sharp.** Steam moves reprice across books in seconds; the follower's fill is 1–1.5 points worse, which the bookmaker literature says "are likely to lose in the long run." (SBR) "If a pro hits the 49ers at -6.5... If you only have access to a -7, yes you are on the sharp side, but you're getting a worse number." (VSiN)
2. **No empirical support.** No published study was found showing tailing (public signals, touts, or Discord groups) beats the closing line after slippage. The only quantitative claims found are marketing.
3. **Fake sharps.** "Almost all touts lie about their won-loss records... if a tout doesn't show a complete and honest record of all of their picks, be suspicious. And even if they do, who's to say the record is real." (VSiN) Survivorship bias: only hot streaks get posted; losers go quiet; "locks" and "whale plays" are sales language, not probability.
4. **Peabody's three arguments** (SportsHandle): a bettor who can truly beat the market (a) doesn't need to sell picks, (b) wouldn't want to — selling dilutes their own edge, and (c) their own wagers move the line before buyers can act. Even elite pros run only ~3–5% ROI — there is no 60%-win-rate product to buy.
5. **Manufactured signals.** Some "steam" is engineered to harvest followers; tailing it means buying the wrong side of someone else's middle. (docsports)

**Narrow legitimate uses:** following respected *originators'* line moves as an information input (not a pick) — i.e., "Pinnacle moved, so update your fair price and look for slow books" — is just the top-down +EV workflow from §2 with extra steps. Public pick-tracking (Pikkit-verified, exchange-synced records) is the only tailing-adjacent evidence worth anything, and even that documents the tracker's own execution, not a seller's.

**Practical and plausible?** No — not as a primary strategy. The economics are broken: real edges are 3–5%, slippage eats 1–1.5 points, signals are delayed, and the sellers' incentives are misaligned. Spend the subscription money on a second sportsbook account and an odds screen instead.

---

## 10. Deep analysis: where genuine +EV with high CLV-beating probability actually comes from

### The unified theory
The research converges on one uncomfortable truth: **the edge is in execution and access, not prediction.** Models can't beat the sharp close (public evidence: nil); line-shoppers can beat the prices at soft books (public evidence: strong, Buchdahl's 952-bet study). So the highest-probability +EV bets are the ones where you are faster, better-shopped, or better-positioned than the market — not the ones where you think you're smarter.

### The four genuine edge sources, ranked by evidence
1. **Line-shopping the stale slow book (strongest evidence).** When Pinnacle/Circa move and a recreational book lags, the lagging price is +EV vs the new fair. Best windows: first ~30 min after open, off-peak hours after major-book moves, breaking injury news where one book is slow. Risks: voided "bad lines," instant limitation, and the trap risk — confirm the laggard is slow, not informed. (Buchdahl; SBR)
2. **Timing the move (strong evidence).** NFL closers beat openers 65.9% vs 63.5%. If you can anticipate direction — favorites early, underdogs late in MLB; betting before the steam rather than after — you manufacture CLV. This is the "bet early when you expect the market to move against your position" rule.
3. **Soft derivative/prop markets (good evidence, low capacity).** Props are mispriced because books can't set hundreds perfectly; early prop lines are rough estimates. But limits are tiny, limitation is instant, CLV is unreliable there (no true price being discovered), and some "inefficiencies" are inside information (the Porter/Rozier warning). Use for small stakes, never as the core.
4. **Exchange market-making (good theory, execution-dependent).** On Novig: post both sides near fair in liquid markets, collect the maker credit, cancel around news. You earn the spread and the credit instead of paying the taker fee and the adverse-selection tax. This is the only strategy where the trap dynamic works *for* you — provided you manage toxicity (pull quotes through news).

### The trap-avoidance filter, as an algorithm
For every candidate bet, compute: `edge = fair_no_vig_prob − offered_price` (in probability terms). Then apply:
1. If edge < 2% → pass (friction).
2. If edge > 5% → assume your error; require an explanation before betting.
3. If the quote is off-market vs *every* sharp reference → pass until you can explain the counterparty's motive.
4. If material news occurred in the last 30–60 min and this quote didn't move → pass (assume informed counterparty).
5. If the order has rested untouched through a market move (exchange) → pass or fade, don't take.
6. If it's a parlay/SGP/boosted ticket → recompute hold; default pass unless a correlated edge is documented.
Otherwise → size at fractional Kelly (quarter default), log vs Pinnacle close, and move on.

### The CLV-maximizing portfolio
- **Core (70–80% of volume):** main-line spreads/totals/moneylines via top-down line shopping, bet early, 2.5–5% edges, quarter-Kelly. This is where CLV is measurable and meaningful.
- **Satellite (10–20%):** prop edges from early lines, small stakes, flat 0.5–1%, accepted as high-variance lottery tickets with unreliable CLV.
- **Market-making (on Novig):** resting two-sided quotes near fair in deep books, earning credits; sized small; cancelled through news. This is the "making" half of the app.
- **Excluded:** parlays/SGPs (unless correlated edge), odds boosts (unless genuinely +EV applied to longest odds), futures (unless mispriced + hedge plan), tailed picks (never), steam-chasing (never), bets during unresolved news (never unless fastest).

### The honest base rate
Even elite professionals run ~3–5% ROI. A well-executed retail operation — 5+ books, disciplined shopping, 2.5%+ edges, quarter-Kelly, 1,000+ bets — has a credible path to low-single-digit ROI with CLV confirming skill in ~50 bets. Anyone promising more is selling something. The app's job is to make this execution *frictionless and disciplined*, not to find magic.

---

## 11. App design implications (taking and making)

If this research guides a betting app, these are the features the evidence supports:

1. **Fair-price engine:** pull Pinnacle (and Circa/Bookmaker) quotes; de-vig with Shin or odds-ratio (not equal-margin); store timestamped fair prices. This is the single most important module.
2. **Edge screener:** flag any retail/exchange price beating fair by ≥2% (configurable; ≥3% default for action), with the EV computed net of fees (Novig live taker fee: 0.03×P×(1−P)).
3. **Trap score:** for each flagged edge, compute a risk score from: (a) deviation vs *all* sharp references (off-everywhere = red), (b) quote age and line-move chronology (did the market move 10–30 min ago without this quote?), (c) news recency (any team news in the last hour?), (d) edge magnitude (>5% = red), (e) resting-order age on exchanges. This operationalizes §4.
4. **CLV tracker:** log every bet with timestamp; capture Pinnacle close 5–10 min pre-game; report CLV% segmented by sport/market/bet type; show significance (needs ~50 bets for signal, ~1,000 for confidence). This is the app's truth serum.
5. **Timing module:** tag each bet early/late; remind the favorite-early/underdog-late rule (MLB data); alert on the first-30-minutes-after-open window and on breaking-news windows.
6. **Staking calculator:** fractional Kelly (quarter default) with the 1.5–2% bankroll cap; flat-stake fallback when no tested model exists.
7. **Market-making mode (Novig):** two-sided quote poster near fair ± credit; auto-cancel/reprice on news events and on sharp-reference moves beyond a threshold; toxicity guard (widen or pull when fills skew one-sided, per Glosten–Milgrom).
8. **Pre-bet checklist gate:** the 10-item checklist from §7 as a mandatory confirmation screen. Most "discipline" failures are checklist failures.
9. **What NOT to build:** a tout-following feed, an RLM "sharp pick" alert that fires as a pick, a parlay builder optimizer, or any feature that encourages steam-chasing. The evidence says these lose money.

---

## 12. Contested claims and what could not be verified

1. **How much CLV is "enough."** Buchdahl's framework says any consistent beat of the no-vig close ≈ that EV; practitioners variously demand 2%, 3–5%, or "beat by more than the margin 50%+ of the time." No consensus study pins the CLV%→ROI mapping. The oft-quoted "+1% CLV ≈ 5% profit" could not be verified against a primary source.
2. **CLV's status.** Buchdahl: the central skill metric. Andrews/Unabated: "a guidepost, not gospel" — meaningless in inefficient markets. SBR skeptics: "useful but flawed"; "we don't get paid for BTCL." All agree it beats win-rate as a signal.
3. **RLM/ticket-handle profitability.** Commercially asserted, never rigorously tested. The skeptical prior — signals are mostly noise + manufactured steam — is better supported.
4. **Novig's "no commission" marketing vs the fee schedule.** Resolved factually above (pregame straights fee-free; live/parlay fees exist), but expect marketing to keep saying "no commission." Re-check the fee schedule before acting — fees changed once already with the Aug 2026 relaunch.
5. **Whether small, genuine edges exist at scale for retail.** The 2024–26 replication literature says models can't beat the sharp close; the line-shopping literature says the *prices at soft books* can be beaten. Both can be true — the edge is in execution and access, not prediction.
6. **Model-based vs market-based.** Market-based (Pinnacle no-vig) is the validated practical standard; model-based edges are claimed widely but the public evidence of beating the close with a model is essentially nil.
7. **Not verified:** any controlled study of RLM/ticket-vs-handle ROI vs the closing line; CLV by day-of-week/time-of-day (beyond the MLB last-2-hour finding) or seasonal effects; any independent audit of tailing services' ROI net of slippage; whether Novig's order flow includes an affiliated internal market-maker (as documented at Crypto.com/Kalshi) — plausible, not confirmed.

---

## 13. Key sources

**Books:** Joseph Buchdahl — *Fixed Odds Sports Betting* (2003), *Squares & Sharps, Suckers & Sharks* (2019), *Monte Carlo or Bust* (simulations; the 952-bet CLV study); runs football-data.co.uk — the most rigorous public CLV/market-efficiency treatment. Ed Miller & Matthew Davidow — *The Logic of Sports Betting* (2019): how books actually make lines (they optimize profit, not accuracy); correlated/synthetic positions. Stanford Wong — *Sharp Sports Betting* (2001): the mathematical handbook (Wong teasers; books have since repriced, but the value-discovery method is the lesson).

**People & outlets:** Pinnacle's Betting Resources (pinnacle.com) — the sharp book's own educational articles; best free primary source on market mechanics. Unabated (Rufus Peabody + Captain Jack Andrews) — Unabated Line (vig-free consensus), Edge Tool, CLV calculator; most practitioner-credible +EV toolkit. Bet the Process (podcast, Peabody + Jeff Ma, since 2017) — note: Novig is a show sponsor, discount Novig-related segments. SBR Forum — two decades of sharp-practitioner discussion; invaluable but anecdotal. Pinnacle Odds Dropper / OddAlerts — commercial implementations of the "Pinnacle drops → bet the slow book" workflow Buchdahl validated.

**Papers:** Dmochowski et al., "A statistical theory of optimal decision-making in sports betting," *PLOS ONE* 2023 (DOI 10.1371/journal.pone.0287601) — optimal bet selection; Kelly; books exploit public biases. Wunderlich, Garnica-Caparros & Memmert (2025) — model accuracy ≠ profitability. Wheatcroft (2020), *JQAS* — profit from betting against overreaction in soccer odds. UCD working papers WP23_04 / WP23_12 — favourite-longshot bias; bookmaker profit on mismatches. arXiv 1211.4000 — NFL open/close movement stats (80% move ≤1 pt).

**Datasets:** football-data.co.uk (Buchdahl; 84k+ European soccer matches with odds); tennis-data.co.uk (58k ATP/WTA matches); The Odds API (Pinnacle feeds for DIY fair-price pipelines).

**Novig mechanics:** WSN prediction-markets review of Novig (full page read 2026-10-03); props.com Novig promo/fee pages.

---

*End of report. Research basis: web index (3 pages fetched in full, remainder search-result text), read 2026-10-03. No live-book price checks were performed. Fee schedules, book policies, and Novig's terms should be re-verified before acting, as they change.*
