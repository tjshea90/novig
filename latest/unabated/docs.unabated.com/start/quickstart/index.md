Start building › Quickstart

# Your first odds request.

Build with Unabated market data in a few clear steps. For developers, content partners, and the agents helping them.

Show guidance forFree APIConcierge APIEnterprise API

✦

### Agents welcome.

Read the tier rules and start with a runnable example. These same guides are available in Markdown, alongside a machine-readable OpenAPI contract.

Copy a starter prompt ▢[Markdown index ↗](/llms.txt)[Build with an agent →](/start/agents)

1

## Get your API key

Complete your content partner profile, then generate a key in your account. Already a Concierge subscriber? Your API key management is available in the same place.

[Open API key management ↗](https://tools.unabated.com/api-keys)

Keep the key on your application's server. Set `UNABATED_API_KEY` in your environment; never put it in a public page or repository.

2

## Request an odds snapshot

This example requests NFL moneyline odds. [Discover current leagues and bet types](/guides/discovery) to request another market. Read the returned snapshot from `data.odds`.

cURLJavaScriptPython

Copy

```
curl --fail-with-body --max-time 20 \
  "${UNABATED_API_BASE_URL:?Set UNABATED_API_BASE_URL to https://data.unabated.com}/market/nfl/straight/odds?betTypeId=1" \
  -H "X-Api-Key: $UNABATED_API_KEY"
```

Set `UNABATED_API_KEY` privately and `UNABATED_API_BASE_URL` to `https://data.unabated.com` before running the command. See [API key setup](/start/authentication#api-base-url).

◷

Free odds are delayed by at least 15 seconds. Make one odds request every five seconds per API key, shared across all odds endpoints.

What a successful response looks like

```
{
  "success": true,
  "data": {
    "odds": { "…": "league, event, market, and selection data" },
    "lastUpdated": "…"
  },
  "messages": []
}
```

This is an abbreviated shape, not a fixture to copy into your integration. Follow the [response hierarchy](/guides/response-concepts) to reach available selections. Lines depend on the sport, book, market, and your access scope.

3

## Free API only: publish a link with your partner code

Choose a real Gambly deep link from the response and add the tracking suffix shown on your API keys page before publishing it.

JavaScript · attach attribution onceCopy

```
const url = new URL(deepLink);
url.searchParams.set("utm_campaign", partnerCode);
const publishedLink = url.toString();
```

⚠

**Free partners: add your tracking code.** Untagged clicks do not count toward your traffic. Test the link from your site or platform, then confirm the lifetime count and last-click time in your account.

[See the complete deep-link workflow →](/guides/deep-links)

4

## Add live updates with Concierge API or Enterprise API

Create a filtered subscription, then open its signed SSE URL to receive updates within your API tier's data coverage. Free API access uses REST polling.

[Read the streaming lifecycle guide →](/guides/streaming/lifecycle)

Next: understand the odds response[Explore odds and markets →](/guides/odds)

Selecting a tier above changes the guidance shown here. It does not change your API tier. [Compare access tiers](/start/access-tiers).
