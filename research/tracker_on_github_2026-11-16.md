# Tracker on GitHub: what Novig supplies, what the engine can run (DG4, Tj chat 02:38 UTC 2026-11-16)
Goal: accurate Tracker data with as little as possible processed and stored on the phone. From NOVIG_API.md §5/§16/§17 (docs; settlement shape not seen live, retention depth not verified).

## Novig can supply (subaccount, read with a trading::read key, the engine already holds one)
- Every order, open and settled (`GET /v3/orders?status=FILLED|CANCELED|REJECTED`), every fill with price, qty, taker/maker, fee (`GET /v3/portfolio/fills`), positions (qty, cost), and the ledger (`/transactions`: FILL, FEE, MAKER_CREDIT, SETTLEMENT, TRANSFER_IN/OUT). Hand bets and engine bets alike: same subaccount.
- Result: a SETTLEMENT row pays a win; a loss is "position gone, no settlement row" (locked markets are the exception: never grade those lost from a missing row). Market outcome status (WIN/LOSS/PUSH/FMV payout) is public.
- Day-by-day profit: yes, from fills + settlements + fees + credits, by game day.

## Novig cannot supply (must be computed and kept by us)
- EV and fair price when the bet was placed, the books/rules behind it (`atBet`), stars, bet source/kind labels, closing line and CLV (closing comes from the public trades archive / PropLine / Pinnacle), the repeat/similar-bet family keys.

## What GitHub can run
- Engine pass: read orders/fills/ledger by cursor, join with its own scan record of the bet (engine bets: trades.json already has EV and rules; hand bets: only price/stake/result from Novig plus whatever the phone sent via phone-data), grade from SETTLEMENT + public market status, write a tracker file to cloud-feed/phone-data; closes and CLV via engine-grade.yml as today.
- Phone then only reads and shows that file (a few hundred KB), keeps nothing but open bets it needs for the Bet sheet guards.

## Unverified before building
1. How far back fills/settled orders/ledger go (retention; paging cost: fills cost 8 + 1 per 50 rows of the `history` bucket, so a big backfill must be slow and paced).
2. Real SETTLEMENT row shape (never seen live).
3. Hand-bet detail (EV at bet) is lost unless the phone sends it at bet time (tiny record to GitHub).
Recommendation: engine builds `tracker.json.gz` from Novig's fills+ledger joined with its own and the phone's bet-time records; the phone's bets.json shrinks to open bets. Verify 1-2 on the first real read, keep the phone's file until the engine's totals match it for a week.
