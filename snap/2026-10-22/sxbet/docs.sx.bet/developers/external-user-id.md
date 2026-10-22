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
[single-account model](/developers/account-models.md#single-account).

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

> **[Posting orders](/developers/posting-orders.md)**
>
> The submit flow this field rides on.

> **[Client order id](/developers/client-order-id.md)**
>
> A per-order tag. Unique, charset-restricted, and absent from the bet grain.

> **[Bet grains](/developers/which-grain.md)**
>
> Bets, fills, and positions — which row to read for a per-user history.

> **[Create orders](/api-reference/post-orders-v3.md)**
>
> Every field, every validation rule.

> **[Account models](/developers/account-models.md)**
>
> The two ways to represent your site's users on SX.bet.

> **[Work with us](/developers/work-with-us.md)**
>
> Building at scale or exploring a partnership.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.