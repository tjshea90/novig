> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Book

> Every order-book change on a market.

The `book` channel reports every change to a market's order book.
It costs 16 tokens per market and includes [`lifecycle`](https://docs.novig.com/api/streaming/lifecycle).

Each outcome has its own ladder, a list of resting orders by price.
The two ladders show one market from opposite ends.

A bid resting at `0.665` on one outcome is liquidity for the other outcome.
A taker on the other outcome trades against it at `0.335`.
A taker is an order that trades against a resting order.

Kansas City Chiefs*3f2504e0…01***0.670***250***0.665***110***0.660***250*

Buffalo Bills*3f2504e0…02***0.340***90***0.335***180***0.325***400*

Each outcome's array lists the best price first.
Within a price, it lists the earlier order first.
The array order **is** the queue.

## Snapshot

```json theme={"dark"}
"book": {
  "seq": 48120,
  "orders": {
    "3f2504e0-…": [
      {
        "orderId": "7c9e6679-…",
        "price": "0.665",
        "qty": 110
      },
      {
        "orderId": "1b4e28ba-…",
        "price": "0.660",
        "qty": 250
      }
    ],
    "5a1c9e73-…": [
      {
        "orderId": "6ba7b810-…",
        "price": "0.340",
        "qty": 90
      }
    ]
  }
}
```

Each key in `orders` is an outcome ID.

The snapshot holds the best `depth` price levels of each outcome.
Set `depth` on `subscribe` or `snapshot`, from 1 to 20. It defaults to 20.
Deltas cover every level, so ignore an `update` or `remove` for an order you don't hold.

An outcome with no resting orders has no key.
Treat a catalog outcome missing from `orders` as an empty book at the snapshot's `seq`.

## Delta

```json theme={"dark"}
"book": {
  "seq": 48121,
  "deltas": [
    {
      "kind": "add",
      "orderId": "aa0e8400-…",
      "outcomeId": "3f2504e0-…",
      "price": "0.670",
      "qty": 250
    },
    {
      "kind": "update",
      "orderId": "1b4e28ba-…",
      "remaining": 150
    },
    {
      "kind": "remove",
      "orderId": "7c9e6679-…",
      "reason": "fill"
    }
  ]
}
```

| `kind` | `reason` | Means |
| - | - | - |
| `add` | — | Joins the back of its price level |
| `update` | — | A partial fill lowers the resting quantity |
| `remove` | `fill` | Filled in full |
| `remove` | `cancel` | Canceled by the owner or the engine |

Only `book` reports a cancel.

A partial fill arrives as an `update` with the order's new `remaining` quantity.
The order keeps its queue position.
Several fills on one order by one incoming order arrive as one delta.

## Voided orders and your own orders

* [`GOLIVE`, `CLOSE`, and `GRADE`](https://docs.novig.com/api/streaming/lifecycle) void every resting order with no `remove`. Empty the book yourself when one arrives.
* Your own orders appear here like any other order. Match them on the [private stream](https://docs.novig.com/api/streaming/private.md).

## Best bid only

The `bbo` channel reports only the best resting bid of each outcome.
It costs 8 tokens per market and includes [`lifecycle`](https://docs.novig.com/api/streaming/lifecycle).

```json snapshot theme={"dark"}
"bbo": {
  "seq": 311,
  "bbo": {
    "3f2504e0-…": {
      "price": "0.665",
      "qty": 110
    },
    "5a1c9e73-…": {
      "price": "0.340",
      "qty": 90
    }
  }
}
```

```json delta theme={"dark"}
"bbo": {
  "seq": 312,
  "deltas": [
    {
      "outcomeId": "3f2504e0-…",
      "bbo": {
        "price": "0.670",
        "qty": 250
      }
    },
    {
      "outcomeId": "5a1c9e73-…"
    }
  ]
}
```

`qty` is the total resting quantity at the best price.
A delta without `bbo` means the outcome has no resting bid left.
An outcome with no resting bid has no key in the snapshot.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.