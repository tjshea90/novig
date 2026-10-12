# PropLine vs ParlayAPI for pregame EV scanning (Tj, 2026-10-12)
Sources: PARLAY_API.md and RESEARCH.md §23, §43, §45 (measured with Tj's keys); prop-line.com/llms.txt (vendor claims, unverified). Cost estimates are mine, from those figures.

| | PropLine | ParlayAPI |
| :- | :- | :- |
| Plans | Free 1k req/day, Hobby $9 5k/day, Pro $19 25k/day, Streaming Lite $39 250k/day | Free 1k credits/MONTH, Starter $5 20k/month (Tj's), Pro $20 100k, Business $40 1M + streams |
| Game lines | 1 request per league, all events | 3 credits per league (5 with alternates), up to 10 books |
| Player props | per event (fan-out, ~1 request per game) | whole league in ONE call, 3 credits, every book |
| Books | 114 (Pinnacle, DK, FD, MGM, Fanatics, Hard Rock, Bovada, BetOnline...) | similar US set + Pinnacle, ProphetX, bet365, Kalshi |
| Pregame freshness | DK, FD, Fanatics, Kalshi, Polymarket pushed ~1 s; the other ~109 polled 60-90 s. (Our 16:20Z probe measured Pinnacle/BetRivers 0-1 s too; RESEARCH.md §43 notes it runs ~20 s behind ParlayAPI's stamped quotes) | each quote carries its book's own last_update / observed age; stale books dropped via /v1/meta/source-quality |
| Sharp fair | /ev picks Pinnacle -> Polymarket -> Kalshi -> Bovada | Pinnacle quotes + closing lines |
| CLV | /odds/closing, /v1/clv/grade, resolved-props CSV (archive from Apr 2026) | /closing-lines (Pinnacle), historical file, 7-day history on Starter |
| Weakness | props cost a request per game; names unreliable across books; redacted analytics on Free | credits are a monthly budget (Starter ~18 full scans/day if each is ~36 credits); 503s still charged |

Scan cost, 6 leagues with props (~12 games each): ParlayAPI ~36 credits -> Starter lasts ~550 scans a month (CreditPace already paces it). PropLine ~6 + 72 = ~80 requests -> Hobby covers ~60 scans a day, Pro ~300, Lite ~3,000.

Verdict
- Volume and price: PropLine wins clearly. A $9-19 PropLine plan gives more scans than ParlayAPI Starter's whole month, and the daily reset is forgiving.
- Props breadth per call, measured per-quote age, and being already tuned in the app: ParlayAPI wins; it is first in the merge order for that reason.
- Fair quality is a tie that neither decides: both lean on Pinnacle, and Pinnacle is polled at PropLine (pregame 60-90 s) while the chain's better sources (Pinnwire/SGO/OddsPapi) are fresher. Pregame, 60-90 s matters little except right before a start.
- Recommendation: keep both on the chain (ApiChain sleeps the lower one when the better answers). If scanning more often than ~18 times a day is wanted, upgrade PropLine to Hobby/Pro ($9/$19) before ParlayAPI Pro ($20); Streaming Lite's $39 is only worth it for the pushes (live), not for pregame.
- Unverified: PropLine Pinnacle prop coverage (the vendor page doesn't confirm it; RESEARCH.md §23.1 saw 96,925 Pinnacle markets listed), and its real pregame latency per book. A cheap test: Settings > APIs Test key plus the app's freshness line.
