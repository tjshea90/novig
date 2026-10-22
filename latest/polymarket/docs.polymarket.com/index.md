> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Overview

> Build on the world's largest prediction market. Trade, integrate, and access real-time market data with the Polymarket API.

🇺🇸

Looking for&#x20;

Polymarket US

&#x20;

documentation?

Visit US Docs →

[Open](https://docs.polymarket.us)

# Polymarket Documentation

Build on the world's largest prediction market. APIs, SDKs, and tools for prediction market developers.

## Developer Quickstart

Make your first API request in minutes. Learn the basics of the
Polymarket platform, fetch market data, place orders, and redeem
winning positions.

[Place Your First Order →](/trading/quickstart.md)

```typescript TypeScript theme={null}
import { createPublicClient } from "@polymarket/client";

const client = createPublicClient();

const pages = client.listMarkets({ closed: false });
const { items: markets } = await pages.firstPage();
```

```python Python theme={null}
from polymarket import AsyncPublicClient

async with AsyncPublicClient() as client:
    markets = client.list_markets(closed=False)

    async for market in markets.iter_items():
        print(market.question)
```

```bash API theme={null}
curl -G "https://gamma-api.polymarket.com/markets/keyset" \
  --data-urlencode "closed=false" \
  --data-urlencode "limit=5"
```

## Get Familiar with Polymarket

Learn how Polymarket works, explore the concepts behind markets and
trading, or choose how to integrate.

> **[Polymarket 101](/polymarket-101.md)**
>
> Learn how prediction markets work and how to use Polymarket.

> **[Core Concepts](/concepts/markets-events.md)**
>
> Understand markets and events, prices and order books, positions,
> orders, and resolution.

> **[SDKs & APIs](/getting-started/sdks-apis)**
>
> Choose the integration approach that best fits your application.

[![Banner](https://mintcdn.com/polymarket-292d1b1b/FOMte3ewbG-LVy3k/images/banner.png?fit=max\&auto=format\&n=FOMte3ewbG-LVy3k\&q=85\&s=d83f2f21e8474e998d8ba0f45810d978)](https://builders.polymarket.com)

![](/images/icons/medal.svg)

### [Builder Program](https://builders.polymarket.com)

Build apps on Polymarket and earn rewards for driving volume

![](/images/icons/quiz.svg)

### [Help Desk](https://help.polymarket.com)

Get support, report issues, and find answers to common questions

![](/images/icons/sensor.svg)

### [Status](https://status.polymarket.com)

Check API uptime, service health, and incident reports


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.