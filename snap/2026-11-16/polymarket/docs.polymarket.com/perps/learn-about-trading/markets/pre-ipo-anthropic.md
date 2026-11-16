> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Anthropic (ANT): Pre-IPO Specifications

> Specifications for active Polymarket Pre-IPO perpetual futures markets.

## Overview

Pre-IPO Perpetuals are cash-settled, non-expiring contracts that let traders take a view on a private company’s valuation 24/7, without holding shares or accessing gated secondary markets. Positions confer no equity: no voting rights, dividends, information rights, IPO allocation, or claim on the issuer.

Because private rounds rarely disclose a reliable share count, contracts are priced by total valuation: \$1 of price equals \$1B in fully diluted value (e.g. 1,650 = \$1.65T).

Markets are margined and settled in pUSD. ANT’s reference price comes from Entropy’s Anthropic pre-IPO market on Hyperliquid. On Monday, November 16, 2026, ahead of Anthropic’s expected November 19 IPO, ANT will rebase to a per-share equity perpetual priced from Pyth’s ANT/USD feed, without delisting (see *What Happens Post-IPO?* below).

### How pricing works before the split

This section covers ANT until the November 16 rebase, after which it is priced from Pyth as a standard equity perpetual.

* **Index price:** Polymarket’s reference price, published every 200 ms. For ANT, this is Entropy’s oracle price for its Anthropic market on Hyperliquid (`io:ANTH`), which blends a 5-minute EMA of Entropy’s order-book mid with an aggregate of private-market sources (other derivatives, private marks, funding rounds, secondary trades), clipped to fixed catastrophic bounds. [Entropy methodology](https://docs.entropy.io/pre-ipo-perp-mechanics/oracle-price)
* **Mark price:** Used for PnL, margin, liquidations and TP/SL triggers; updated every 200 ms. It is the median of: (1) the Index adjusted by a 150-second EMA of our book mid’s spread to the Index; (2) the median of our best bid, best ask and last trade; and (3) Entropy’s mark for `io:ANTH`. Unavailable inputs fall back to the Index. [Mark price](https://docs.polymarket.com/perps/learn-about-trading/mark-price.md)
* **Funding:** Polymarket’s standard funding. Every 5 seconds we sample our book’s premium (impact prices for 1,000 pUSD notional) versus the Index. Hourly, samples are averaged, run through the 8-hour formula with a 0.5 non-crypto scale, divided by 8, and capped at ±4%/hour. Paid hourly, peer-to-peer: longs pay shorts when the perp trades above the Index, and vice versa. [Funding](https://docs.polymarket.com/perps/learn-about-trading/funding.md)
* **Oracle bounds:** Entropy clips its oracle and mark to fixed catastrophic bounds, which shouldn’t bind normally. They affect ANT only via the Entropy prices we read; our Mark isn’t clipped directly. [Entropy methodology](https://docs.entropy.io/pre-ipo-perp-mechanics/oracle-price)
* **Pre-listing window:** Three calendar days before an announced listing, Entropy’s oracle switches to a 1-hour EMA of its mark (bounds still applied). For a November 19 listing, this likely starts early on November 16 (UTC), so it may affect ANT’s Index for several hours before our switch to Pyth that morning. If Entropy rebases or changes its methodology before then, we may move the switch earlier and will give as much notice as possible.

## Specifications

| Parameter | Asset |
| - | - |
| Market | ANT (API symbol `ANT-USD`) |
| Status | Live (since October 29, 2026) |
| Description | ANT references the fully diluted equity valuation of Anthropic, PBC, quoted in USD with \$1 of price = \$1B of valuation. ANT is currently a pre-IPO market. On Monday, November 16, 2026, it will be rebased via stock split to reference 1 share of Anthropic, PBC Class A common stock (Nasdaq: ANT), quoted in USD and priced from Pyth. Anthropic develops frontier AI models and the Claude family of AI products. |
| Quote Unit | \$1 = \$1B fully diluted valuation until the November 16 split; USD per share after |
| Collateral | pUSD |
| Trading Hours | 24/7 |
| Price / Size Precision | 0.1 price; 0.00001 size; \$10 minimum order notional (may change after the split) |
| Oracle (until split) | Entropy’s oracle price for its Hyperliquid market `io:ANTH`; Entropy’s mark is one of three Mark price inputs |
| Oracle (from split) | Pyth ANT/USD, per-share, 24/7 (ID: 3618). Rebased Nov 11, 22:00 UTC to the fully diluted share count in Anthropic’s amended S-1 (factor 0.94025511). The same feed continues through the IPO, with the Nasdaq price as its leading input once trading opens. |
| Initial Price | 1,805.20 (\$1.81T valuation) |
| Oracle Bounds (Lower / Upper) | 300 / 4,200 (\$300B / \$4.2T); Entropy’s bounds, removed at the split |
| Funding | Standard non-crypto formula (0.5 scale); paid hourly, capped at ±4%/hour |
| Margin Mode | Cross & Isolated |
| Max Leverage | 10x, tiered by position notional: 10x to \$500K, 5x to \$1M, 3x to \$3M, 2x to \$5M, 1x above |
| Maintenance Margin | 5% |
| Anticipated Listing Event | IPO on Nasdaq Global Select Market (ticker ANT) |
| Anticipated Listing Date | November 19, 2026 (pricing expected November 18) |
| Rebase (Stock Split) | Monday, November 16, 2026, \~10:00am ET (target). Ratio = fully diluted share count from the amended S-1 ÷ 1B, announced in advance |
| No-IPO Resolution Date | August 18, 2028 ([details](https://docs.entropy.io/pre-ipo-perp-mechanics/no-ipo-resolution)) |

## What Happens Post-IPO?

### Rebasing to share price with a stock split

Before the split, ANT is quoted in valuation terms (\$1 = \$1B). On Monday, November 16, Polymarket uses its stock split feature to rebase the market so that it references 1 share of Anthropic Class A common stock, quoted in USD, and switches its price feed from Entropy to Pyth. This happens on the existing market: there is no delisting or relisting, and open positions and history carry over.

The split ratio is the fully diluted share count divided by 1 billion, applied exactly (not rounded) and announced in advance. At the split:

* Position sizes are multiplied by the ratio and rounded toward zero at the market’s size precision. Any leftover fraction (well under one lot) moves to the insurance fund.
* Entry prices and the market price are divided by the ratio.
* Position value and unrealized PnL are unchanged apart from that rounding. The conversion realizes no PnL and charges no fee.
* Each converted position gets a split-adjustment entry in trade history showing size and entry price before and after.
* Charts, mark history and 24h stats, including pre-split history, are shown in per-share terms, so there’s no artificial price drop. Past trades keep their original prices.

The fully diluted share count is taken on the same basis as Entropy’s methodology: the total as-converted, fully diluted share count stated in the company’s filing (for Anthropic, the “Total Class A and Class N Common Stock (As-Converted, Fully Diluted)” figure in its amended S-1 filed November 10, 2026), assuming no exercise of the underwriters’ option. Because this count is fully diluted, the implied market cap may differ from the figure shown by public data providers, which typically use shares outstanding. For example, at the top of Anthropic’s \$70–78 range, reported valuations were about \$1.4T fully diluted versus about \$1.02T on shares outstanding.

### The trading pause

There will be a brief trading pause while the split is applied. We will announce the time and split ratio in advance. The pause has three phases:

1. **Paused.** All ANT orders, including TP/SL, are cancelled and not restored; new orders are refused. Funding settles up to the pause, then stops. Positions aren’t liquidated via the order book and are converted during this phase. Affected accounts can deposit and add margin but can’t withdraw or remove margin.
2. **Reopening.** Once valid per-share prices are flowing, only post-only limit orders are accepted, for at least 5 minutes, with no fills. Orders must be explicitly marked post-only (API: `po=true`); unmarked orders are rejected even if they wouldn’t cross the book. Market, IOC/FOK, TP/SL and TWAP orders are refused. The timer restarts if a price or health check fails.
3. **Live.** After the minimum window, an operator releases the market once prices are fresh and both sides are quoted within configured spread and depth. Fills and liquidations resume, funding restarts with no catch-up charge, and withdrawal limits clear.

Cross-margin emergency takeover and auto-deleveraging can still occur during Paused and Reopening under normal rules. Other markets trade normally throughout.

### Timing for ANT

We plan to rebase ANT on **Monday, November 16, 2026, at \~10:00am ET**, about 72 hours before Anthropic’s expected first Nasdaq trade. This allows several days of per-share price discovery and avoids changes during IPO-day volatility. We’ll confirm the exact time and split ratio in advance.

| When | What happens |
| - | - |
| Wed, Nov 11, 22:00 UTC | Pyth rebases its ANT/USD feed (×0.94025511) to the fully diluted share count in Anthropic’s amended S-1. |
| By Fri, Nov 13 | Polymarket announces the split ratio and pause time. |
| Mon, Nov 16, \~10:00am ET | ANT pauses, positions are converted, the price feed switches from Entropy to Pyth, and the market reopens (post-only, then Live). |
| Wed, Nov 18 (expected) | Anthropic prices its IPO. |
| Thu, Nov 19 (expected) | ANT begins trading on Nasdaq. |

Between the split and the first trade on Nasdaq, ANT is priced from Pyth’s ANT/USD feed, which tracks Anthropic’s pre-IPO per-share value until the listed price is available.

If the final prospectus share count differs from the count used for the split, the contract itself is unaffected: after the split ANT references 1 share, and from the opening trade it is priced from the actual Nasdaq share price.

### Oracle switch to Pyth

From the split on November 16, ANT is priced from Pyth’s 24/7 ANT/USD feed (ID: 3618), and Entropy is no longer used: Entropy’s bounds no longer apply, and Entropy’s mark is no longer an input to our Mark price. No second switch is needed at the IPO. Pyth keeps the same feed and, once Nasdaq trading opens, uses the listed price as its leading input. From then on ANT behaves as a standard Polymarket equity perpetual, and the feed used for the Index can differ by session (regular hours, overnight, weekend). Funding already uses the standard formula, so it does not change.

**Note for market makers:** Entropy keeps its own Anthropic market in valuation units after the IPO (share price × fully diluted shares ÷ 1B). After our split, ANT is quoted per share, so hedging across the two venues needs to apply the split ratio.

### Worked example

*Illustrative numbers only.* Suppose the fully diluted share count is 18,000,000,000, so the split ratio is 18.

| Moment | Price source | ANT price | Your position | Notional |
| - | - | - | - | - |
| Before split (Mon, Nov 16) | Entropy (\$B valuation) | 1,701.00 | 10 ANT long | \$17,010 |
| After split (pre-IPO) | Pyth (per-share) | 94.50 | 180 ANT long | \$17,010 |
| Nasdaq opens at \$80 | Pyth (listed price leads) | 80.00 | 180 ANT long | \$14,400 |

After the split, each \$1 move in Anthropic’s share price moves a 1-unit position by \$1, exactly like holding 1 share.

### If the IPO is delayed or withdrawn

ANT keeps trading in per-share terms on Pyth’s ANT/USD feed. If a later filing changes Anthropic’s share count, Pyth may rebase its feed again; we will announce in advance how ANT is handled. If the company remains unlisted at the No-IPO Resolution Date, the market settles as described in the table above.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.