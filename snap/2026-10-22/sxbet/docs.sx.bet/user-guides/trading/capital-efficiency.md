> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Managing Positions

> Learn how SX Bet locks collateral and how you can unlock capital by trading both sides of a market.

## What is Capital Efficiency?

When you place a bet, SX Bet only locks the amount of money you could actually lose — not your full stake on every individual bet. If you place bets on both sides of the same market, only your worst-case loss stays locked. Any extra collateral is automatically refunded to your wallet.

This means your funds are freed up and available to use again, even before your bets settle.

## How It Works

When you have bets on opposing outcomes in the same market, SX Bet:

1. Looks at all your positions on that market together
2. Calculates the worst possible loss across those positions
3. Keeps only that amount locked as collateral
4. Refunds the rest to your wallet instantly

This applies to market orders, limit orders, partial fills, and both pre-match and live markets.

**Quick Example**

You place two bets on the same match — \$25 on Team A at 1.96 odds, and \$25 on Team B at 1.95 odds.

Since only one team can win, the most you can lose is \$1.33. SX Bet recognizes this and only keeps \$1.33 locked as collateral — refunding the remaining \$48.66 to your wallet right away.

![](https://mintcdn.com/sxbet-9c561d83/nTchO44e8z9Jwtov/public/user-guides/capital_efficiency_refund.png?fit=max\&auto=format\&n=nTchO44e8z9Jwtov\&q=85\&s=8637bb773f15428ace557ec755d5a15c)

## Exiting a Position Early

Want to lock in a profit or cut your losses before a match ends? You can exit your position at any time by placing a bet on the opposing outcome. Capital efficiency handles the rest — your worst-case loss stays locked and any excess collateral is returned to your wallet right away.

**How to exit a position:**

1. Go to [sx.bet/my-bets/unsettled](https://sx.bet/my-bets/unsettled) to view your open bets

![](https://mintcdn.com/sxbet-9c561d83/etKfZW0IBkJgvVe3/public/user-guides/unsettled_bets.png?fit=max\&auto=format\&n=etKfZW0IBkJgvVe3\&q=85\&s=3df55a0ed45e27d6ef411b4c49e79970)

2. Expand the dropdown and select the bet you want to exit

![](https://mintcdn.com/sxbet-9c561d83/nTchO44e8z9Jwtov/public/user-guides/select_your_bet.png?fit=max\&auto=format\&n=nTchO44e8z9Jwtov\&q=85\&s=fae8b25922badd8be1c950de60db396c)

3. Place a bet on the opposing outcome

![](https://mintcdn.com/sxbet-9c561d83/nTchO44e8z9Jwtov/public/user-guides/place_opposing_bet.png?fit=max\&auto=format\&n=nTchO44e8z9Jwtov\&q=85\&s=f7a8ffc59552c50a7b421fe3ed06c685)

Once your bet is filled, only your net worst-case loss remains locked. Your freed-up collateral is available immediately — before your original bet even settles.

![](https://mintcdn.com/sxbet-9c561d83/nTchO44e8z9Jwtov/public/user-guides/refund.png?fit=max\&auto=format\&n=nTchO44e8z9Jwtov\&q=85\&s=538ce4dce45bbc9a675e2ee29229b726)

> **Example: Exit to Lock in a Profit**
>
> **Pre-match odds: Team A @ 2.10 | Team B @ 1.85**
>
> 1. You stake $100 on Team A. If Team A wins, your payout is $100 × 2.10 = \$210.
>
> 2. After the first half, Team A is winning 2–0. Odds have shifted — Team A: 1.50 | Team B: 3.20. You want to guarantee a profit no matter the result.
>
> 3. To guarantee the same payout on both sides, calculate your hedge bet: $210 ÷ 3.20 = $65.62. You place \$65.62 on Team B at 3.20 odds.
>
>    * If Team A wins: $100 × 2.10 = **$210 payout\*\*
>    * If Team B wins: $65.62 × 3.20 = **$210 payout\*\*
>    * Total staked: $100 + $65.62 = \$165.62
>    * **Guaranteed profit: \$44.38**
>
> 4. After placing the bet on Team B, you instantly receive $165.62 back. Since your worst-case scenario is a push (both bets refunded), none of that $165.62 needs to stay locked.
>
> 5. Your \$165.62 is now free to use on another bet — before the original match even finishes.

> **Example: Exit to Minimize a Loss**
>
> **Pre-match odds: Team A @ 2.10 | Team B @ 1.85**
>
> 1. You stake $100 on Team A. If Team A wins, your payout is $100 × 2.10 = \$210.
>
> 2. After the first half, Team B is winning 2–0. Odds have shifted — Team A: 3.70 | Team B: 1.33. Things aren't looking good, but you can still limit your loss.
>
> 3. To guarantee the same payout on both sides, calculate your hedge bet: $210 ÷ 1.33 = $157.89. You place \$157.89 on Team B at 1.33 odds.
>
>    * If Team A wins: $100 × 2.10 = **$210 payout\*\*
>    * If Team B wins: $157.89 × 1.33 = **$210 payout\*\*
>    * Total staked: $100 + $157.89 = \$257.89
>    * **Guaranteed loss capped at: \$47.89**
>
> 4. After placing the bet on Team B, you instantly receive \$210 back — your full guaranteed payout, returned before the match ends.
>
> 5. In the best case (a push), you receive an additional \$47.89 at settlement on top of that.

## Trading In and Out of Positions

Capital efficiency also lets you move in and out of the same market without needing to lock new capital each time. As long as your worst-case loss is covered, you can:

* Reduce risk without waiting for the market to close
* Re-enter at better prices
* Trade price movements instead of holding all the way to settlement

Think of it like being able to reuse the same bankroll across multiple moves in the same game.

## For Market Makers

If you're quoting both sides of a market, capital efficiency means you don't need to lock capital for every individual order. Instead, only your net worst-case exposure is locked. This lets you:

* Quote both sides with less capital tied up
* Provide continuous liquidity across outcomes
* Deploy larger position sizes with the same bankroll
* Scale your activity without repeated deposits or manual capital management

> **[Market Orders](/user-guides/trading/market-orders.md)**
>
> Bet at the best available odds and get matched instantly.

> **[Fees](/user-guides/trading/fees.md)**
>
> Understand the SX Bet fee structure, including 0% on single bets.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.