> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# External user id

> Tag orders with your own user id.

`externalUserId` is an optional tag you attach to an order on
[`POST /orders-v3`](/api-reference/post-orders-v3). The tag is
a value you choose so that, when one SX wallet places orders for many people on your platform, you
can tell the resulting orders, fills, and trades apart.

`externalUserId` has no effect on funds, fees, or matching. It is the mechanism behind the
[single-account model](/developers/account-models#single-account).

## Example

```javascript JavaScript theme={null}
await fetch(`${API}/orders-v3`, authed({
  method: "POST",
  body: JSON.stringify({
    orders: [{ ...order, externalUserId: platformUserId }],
  }),
}));

const { data } = await fetch(`${API}/trades-v3?perPage=50`, authed()).then((r) => r.json());
const forUser = data.trades.filter((t) => t.externalUserId === platformUserId);
```

## Related

<CardGroup cols={2}>
  <Card title="Posting orders" icon="paper-plane" href="/developers/posting-orders">
    The submit flow this field rides on.
  </Card>

  <Card title="Client order id" icon="hashtag" href="/developers/client-order-id">
    A per-order tag. Unique, charset-restricted, and absent from the bet grain.
  </Card>

  <Card title="Bet grains" icon="layer-group" href="/developers/which-grain">
    Bets, fills, and positions — which row to read for a per-user history.
  </Card>

  <Card title="Create orders" icon="file-plus" href="/api-reference/post-orders-v3">
    Every field, every validation rule.
  </Card>

  <Card title="Account models" icon="sitemap" href="/developers/account-models">
    The two ways to represent your site's users on SX.bet.
  </Card>

  <Card title="Work with us" icon="envelope" href="/developers/work-with-us">
    Building at scale or exploring a partnership.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.