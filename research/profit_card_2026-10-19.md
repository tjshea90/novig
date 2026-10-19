# The Tracker's profit card, rebuilt (PL, 2026-10-19)

Tj, 15:30Z, with a screenshot of Tracker › Stats › Today: "This is broken. It is illogical. Today's slate has 0 settled bets and should show 0 profit or loss because it is only around 11am Eastern time. The
times shown in the chart seem strange too. ... Think of the most logical way to make a chart which shows daily profit/loss and total all time profit/loss that is logical and shows an accurate number so I
know if I'm making or losing money on my bets. I'd like the total all time profit loss to show the difference between my cash balance now from the initial slate of bets logged on day 1 (assume the total
stake of all bets on day 1 was my total deposit into the account)".

## What was wrong (v1.0.0 to v1.0.5)

1. **"This slate" was not today.** `SlateProfit.current` picked the newest slate whose first game had STARTED. At 11 am Monday no Monday game had started, so the card showed Sunday's slate, with Sunday's
   profit, under the Tracker's "Today" chip.
2. **The times had no day.** The line ran "9:30 AM" to "2:22 AM": Sunday's first game to its last result, after midnight, with nothing saying which day either was.
3. **Two sets of period chips.** The Tracker's Today / 7 / 30 / All filtered the cards by when a bet was PLACED; the card had its own This slate / Last slate / 7 / 30 / Every slate by GAME day. A bet
   placed today on Saturday's game was "today" for one and "Saturday" for the other.
4. **Spikes.** Late grades are placed at their game's start plus three hours, so several results land at one moment; the line stepped through them one by one in list order.
5. **The headline was the money staked**, not the money made.

Open stakes were never counted as a loss in the slate maths (only settled bets have a profit), but nothing on the card said so, and the old "balance" chained from day 1's stakes.

## Why not day 1's stakes as the deposit

Day 1's bets totalled **$5.55**; the Vigilant wallet holds about **$198.63 cash + $42.55 in open bets** (2026-10-19). Taking $5.55 as the deposit would show about **+$235** of profit that was never made.
Novig's ledger records every transfer into and out of the wallet (`TRANSFER_IN` / `TRANSFER_OUT` rows of `GET /v3/account/subaccounts/{keyId}/transactions`, written when a transfer is applied; any key
may read it; `research/novig_docs_2026-10-12/pages/api__subaccounts__funding.md`), so the money put in is READ, not assumed. That is the same sum Tj asked for (money now minus money put in), with the real
deposit.

## What it is now (v1.0.6)

- **A day is its games' day**, Eastern, 5 am to 5 am (`SlateProfit.dayOf`): a 10 pm game that ends after midnight, and a 1 am tennis match, stay on the night they began.
- **Today = today's games** (`SlateProfit.today`), started or not: **$0.00 until a result**, with "$X is at stake on N open bets, not counted until they settle".
- **An open bet is money at stake, never a loss**: it counts on its game's day once it settles. Open bets on later games get their own line ("Also N open bets on later games").
- **One set of period chips**: the Tracker's Today / 7 days / 30 days / All drive the card AND every Stats card, all by game day (`inPeriod` -> `SlateProfit.inLastDays`). A bet on a game after today is
  in All only.
- **All-time profit (headline)** = the Vigilant wallet now (cash + what its open bets cost) minus the money put in, net of money taken out (`WalletProfit`, read by `WalletMoney` in the app: balance,
  positions, two ledger reads; at most every 5 min while the Stats are on screen, at once on pull to refresh and after a transfer; kept across restarts). Fees and maker credits are in it. With no
  wallet reading (betting not set up, Novig refusing, no transfer on record) it is the settled bets' total, and the card says which.
- **Charts**: Today is a running step line from the first game, with the day named on both times ("Fri 11:20 PM" to "Sat 2:20 AM", or "now"); results at one moment are one step. A longer period is
  a running-total line over a bar per day, then the days one by one.
- Diagnostics carries an "All-time profit (wallet)" line with the sum's parts.

Tests: `SlateProfitTest`, `WalletProfitTest`, `ProfitChartTest`, `StickyHeadersTest`.

Not verified on the real API: the ledger's transfer rows (documented, not yet seen from this container). If the card says "Novig's ledger shows no transfer", the rows are not where the docs put them:
read Diagnostics' line and fix `WalletProfit`/`WalletMoney`.
