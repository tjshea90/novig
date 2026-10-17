# Trading on Kalshi as well as Novig, and Novig-Kalshi arbitrage (research only, nothing built)
Tj, chat t1792274217965, 2026-10-17: "do not build it yet, but research".

Status: desk reasoning from the repo's docs (KALSHI_API.md, NOVIG_API.md §8, RESEARCH.md §8-§16). No live measurement of Novig-vs-Kalshi gaps was made in this run; the numbers below are stated as what to expect, not as findings.

## 1. More +EV from trading both?
- Kalshi is already one of Vigilant's FAIR sources (Kalshi quotes help price Novig bets). Trading there too would add a second place to take the SAME edge, not a new edge: when Novig is cheap vs fair we buy on Novig; when Kalshi is cheap vs the same fair we could buy on Kalshi.
- Volume: Novig's depth is thin on props and alt lines; Kalshi lists far fewer sports markets (game lines/totals/spreads, a few props) with 1-2c spreads, so extra volume would be modest and mostly on NFL/NBA/MLB game markets, where Novig already has the most competition.
- Cost: Kalshi charges a taker fee of about 0.07 x P x (1-P) per contract (about 1.75c at 50c; makers on most sports pay less or none), where Novig pregame taker fills are fee-free. So a Kalshi taker edge must be about 1.5-2c bigger to match a Novig one. Maker (resting) orders on Kalshi are the cheaper route and match how the bid desk works.
- Plumbing needed: a Kalshi TRADING key (the stored one is read-only use today; CLAUDE.md forbids engine order code for Kalshi and keys are class C: needs Tj's word), a Kalshi order port with the same client-id/never-resend rule, wallet split and Kelly caps per venue, repeat/hedge guard across venues, state-regulated-venue limits (position limits, KYC state).

## 2. Risk-free arbitrage Novig vs Kalshi
Locking a profit means buying YES on one venue and the opposite outcome on the other for a total below $1.00 after fees. With Novig fee-free pregame and Kalshi ~1.75c at the mid, you need the two asks to sum under about 0.98 on a 50/50 market.
- Is it too good to be true? Not impossible, but expect it to be rare, small and short-lived. Both venues are efficient exchange-style books that already track Pinnacle, spreads are 1-3c each, so the typical combined cost is 1.02-1.06, not under 1.00. Gaps open for seconds after news, injuries or line moves, then close; other bots hold the same data and faster pipes.
- Real risks that make "no risk" false: (1) rules mismatch: Novig has PUSH/void on whole-number lines, Kalshi's ladder strikes resolve at the strike and ties/overtime/settlement-source wording differ, so a "hedge" can win both or lose both; (2) leg risk: the second leg fills at a worse price or not at all (IOC orders both sides, thin books); (3) settlement/dispute delay and Kalshi position and fee rules; (4) capital tied up in two venues; (5) our own matching error (the same game/line must be matched exactly, same period).
- How often: unknown here. We can measure it for free: the engine already holds Kalshi's stream and Novig's books. A read-only study would log, per matched market, cost = Novig ask(A) + Kalshi ask(not A) (and the reverse), net of fee, with ages of both quotes, and count how often net < 1 and for how many seconds. Recommended next step if Tj wants it: add that log to the cloud engine (no orders) for 3-5 days, then decide. Estimated build: small (CloudEngine pass + journal in the existing archive).

## 3. Recommendation
Do not trade Kalshi yet. Run the read-only gap study first; build Kalshi maker bids only if the study shows >0.5% net gaps that last more than a few seconds or Kalshi shows a clear edge vs fair on markets Novig lacks.
