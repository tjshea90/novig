> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Reading the Order Book

> Learn how to read the SX Bet order book to find available odds and liquidity.

Click any market on SX Bet to open its order book. The order book shows two columns — one for each outcome. Each row shows:

* **Dollar amount** — how much liquidity is available at that price
* **Odds** — the odds you would receive at that level

<Frame>
  <img src="https://mintcdn.com/sxbet-9c561d83/nTchO44e8z9Jwtov/public/user-guides/orderbook.png?fit=max&auto=format&n=nTchO44e8z9Jwtov&q=85&s=2e250d114cb3e9be70127c3350a23c75" width="2078" height="2160" data-path="public/user-guides/orderbook.png" />
</Frame>

## Changing Odds Format

SX Bet supports decimal, American, and fractional odds. Your choice applies across the whole site.

<Steps>
  <Step title="Click the settings icon in the navbar">
    <Frame>
      <img src="https://mintcdn.com/sxbet-9c561d83/nTchO44e8z9Jwtov/public/user-guides/settings.png?fit=max&auto=format&n=nTchO44e8z9Jwtov&q=85&s=9bcbd58af8df2175f85aa974d4411bc7" width="3835" height="854" data-path="public/user-guides/settings.png" />
    </Frame>
  </Step>

  <Step title="Select your preferred odds format">
    <Frame>
      <img src="https://mintcdn.com/sxbet-9c561d83/nTchO44e8z9Jwtov/public/user-guides/change_odds_format.png?fit=max&auto=format&n=nTchO44e8z9Jwtov&q=85&s=7da67da2b1bb48e9322c784c74d46242" width="3840" height="1402" data-path="public/user-guides/change_odds_format.png" />
    </Frame>
  </Step>
</Steps>

## The Consensus Line

The orange line across the order book marks the **consensus line**. It is the fair price for the market, based on activity across all books. It's a reference point, not a hard limit. Orders above and below it are all available to bet into.

<Frame>
  <img src="https://mintcdn.com/sxbet-9c561d83/nTchO44e8z9Jwtov/public/user-guides/global_line.png?fit=max&auto=format&n=nTchO44e8z9Jwtov&q=85&s=8eeba4dd5a2ab08aaeafd5069f97d073" width="2076" height="2159" data-path="public/user-guides/global_line.png" />
</Frame>

## Placing a Bet

Click any odds in the order book to open the bet slip pre-filled at that price. If you bet more than what's available at one price level, your order fills across several levels. Any remainder sits as an open limit order.

<CardGroup cols={2}>
  <Card title="How to Place a Market Order" icon="check" href="/user-guides/trading/market-orders">
    Bet at the best available odds and get matched instantly.
  </Card>

  <Card title="How to Place a Limit Order" icon="plus" href="/user-guides/trading/limit-orders">
    Request your own odds and wait for someone to fill your order.
  </Card>
</CardGroup>

***

<Card title="Want to fetch odds for many markets through the SX Bet API?" icon="code" href="/developers/introduction">
  Build on the SX Bet API to fetch odds programmatically. No API key needed.
</Card>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.