> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Book versioning

> How to determine whether an order book update is newer than the one you have.

Every order book payload — the REST snapshots and both `orderbook_v3` channels — carries a `version`: a
single string that increases as the book changes.

```json theme={null}
"version": "00100000000000000000042"
```

It answers one question: **is this payload newer than the book I already hold?**

<Note>
  `version` applies only to the orderbook channels —
  [`orderbook_v3`](/api-reference/channel-orderbook-v3) and
  [`orderbook_v3_event`](/api-reference/channel-orderbook-v3-event) — and the REST snapshot that
  seeds them.
</Note>

## The apply rule

Apply a payload only when its version is **strictly greater** than the one you hold:

```
apply if incoming.version > current.version
```

Compare as strings. A plain string comparison
gives the correct ordering — no need to parse it into a number or `BigInt`.

## Keep state per market, and seed it from REST

`version` is per market, so key your state by `marketHash` before you compare. Seed
from the REST snapshot at startup, then apply only live publications that have a version greater than the seed or previous message.

## Gaps are normal

`version` is not a counter but it is increasing. Consecutive changes on a market jump by arbitrary amounts, not by 1.

## Related

<CardGroup cols={2}>
  <Card title="Recovery & reliability" icon="shield-check" href="/developers/realtime-reliability">
    Recovery, ordering and deduplication across reconnects.
  </Card>

  <Card title="Get orderbook snapshot" icon="layer-group" href="/api-reference/get-market-orderbook-snapshot">
    The REST seed that carries the same version.
  </Card>

  <Card title="Fetching odds" icon="chart-line" href="/developers/odds">
    Seeding and subscribing to the book and best odds.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.