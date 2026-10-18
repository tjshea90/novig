> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Recovery & reliability

> Maintain consistent state across connects and reconnects on the SX Bet real-time API.

How to keep local state consistent across reconnects: enabling recovery, reading the `subscribed`
outcome, deduplicating replayed messages, and fetching history. Per-namespace history settings are on
[Channel and limit reference](/developers/realtime-reference#history-and-recovery-per-namespace); the
snapshot-plus-subscribe seed pattern is on
[Initialization](/developers/realtime-initialization#snapshot--subscribe-pattern).

## Enabling recovery

Pass options to `newSubscription` to control reliability behavior:

| Flag | Type | Description |
| - | - | - |
| `recoverable` | `boolean` | Enables automatic recovery. On resubscribe, the client sends its last known stream position and the server replays missed publications from history. |

Set `recoverable: true` on channels whose namespace has history.

<CodeGroup>
  ```javascript JavaScript theme={null}
  const sub = client.newSubscription(`orderbook_v3_event:${eventId}`, {
    recoverable: true,
  });
  ```

  ```python Python theme={null}
  from centrifuge import SubscriptionOptions

  options = SubscriptionOptions(recoverable=True)
  sub = client.new_subscription(f"orderbook_v3_event:{event_id}", handler, options)
  ```
</CodeGroup>

## Interpreting subscribed after reconnect

After a reconnect, the `subscribed` event fires with context that tells you whether your local state
is still consistent:

* `wasRecovering`: the client attempted to recover from a previous stream position
* `recovered`: the server successfully replayed all missed publications
* `positioned`: the subscription has stream position tracking enabled
* `recoverable`: the subscription supports automatic recovery

<CodeGroup>
  ```javascript JavaScript theme={null}
  sub.on("subscribed", (ctx) => {
    if (ctx.wasRecovering && ctx.recovered) {
      // Reconnected and gap was filled via history replay.
      // No need to re-fetch from REST — all missed messages were replayed.
    } else if (ctx.wasRecovering && !ctx.recovered) {
      // Reconnected but history was pruned before recovery could complete.
      // Too much time passed — re-seed your local state from REST.
    } else {
      // Fresh connect (first connection, or after a clean disconnect).
      // Seed initial state from REST, then rely on the subscription for updates.
    }
  });
  ```

  ```python Python theme={null}
  from centrifuge import SubscribedContext, SubscriptionEventHandler

  async def on_subscribed(ctx: SubscribedContext) -> None:
      if ctx.was_recovering and ctx.recovered:
          # Reconnected and gap was filled via history replay.
          # No need to re-fetch from REST — all missed messages were replayed.
          pass
      elif ctx.was_recovering and not ctx.recovered:
          # Reconnected but history was pruned before recovery could complete.
          # Too much time passed — re-seed your local state from REST.
          pass
      else:
          # Fresh connect (first connection, or after a clean disconnect).
          # Seed initial state from REST, then rely on the subscription for updates.
          pass

  handler = SubscriptionEventHandler(on_subscribed=on_subscribed)
  ```
</CodeGroup>

| `wasRecovering` | `recovered` | State | What to do |
| - | - | - | - |
| `true` | `true` | Recovered | History replay filled the gap — no action needed |
| `true` | `false` | Unrecovered | History was pruned — re-seed from REST |
| `false` | — | Fresh connect | First connection or clean reconnect — seed from REST |

## Delivery guarantees

For namespaces with history enabled, Centrifugo provides **at-least-once delivery** within the
recovery window — missed messages are replayed from server-side history on reconnect. When
`recovered: true`, everything you missed was replayed and you do not need to re-seed from REST. If the disconnect outlasts
it (or the message cap is reached first), `wasRecovering: true, recovered: false` fires and you must
re-seed from REST.

Recovery can fail even after a short disconnect if the server no longer has the
missed publications in history or if the saved stream position is no longer valid. When this happens
`recovered` is `false` and you must re-seed from REST.

## Dedup

At-least-once delivery means a message may occasionally be replayed more than once during recovery.
Every publication includes a `messageId` in `ctx.tags` — use it to deduplicate on the client side:

<CodeGroup>
  ```javascript JavaScript theme={null}
  const seen = new Set();
  const MAX_SEEN = 10_000;

  sub.on("publication", (ctx) => {
    const id = ctx.tags?.messageId;
    if (id !== undefined) {
      if (seen.has(id)) return;
      seen.add(id);
      if (seen.size > MAX_SEEN) {
        seen.delete(seen.values().next().value);
      }
    }
    applyUpdate(ctx.data);
  });
  ```

  ```python Python theme={null}
  from collections import OrderedDict
  from centrifuge import PublicationContext, SubscriptionEventHandler

  MAX_SEEN = 10_000
  seen: "OrderedDict[str, None]" = OrderedDict()

  async def on_publication(ctx: PublicationContext) -> None:
      msg_id = (ctx.tags or {}).get("messageId")
      if msg_id is not None:
          if msg_id in seen:
              return
          seen[msg_id] = None
          if len(seen) > MAX_SEEN:
              seen.popitem(last=False)
      apply_update(ctx.data)

  handler = SubscriptionEventHandler(on_publication=on_publication)
  ```
</CodeGroup>

`messageId` deduplicates any repeated publication, whatever the cause. There is no separate handling
for recovery replay versus other duplicates — one `messageId` per publication, drop the ones you have
already seen.

<Note>
  [`orderbook_v3`](/api-reference/channel-orderbook-v3) and
  [`orderbook_v3_event`](/api-reference/channel-orderbook-v3-event) are the exception: their `version`
  both **orders** and de-duplicates the book. Apply a publication only when its `version` is strictly
  greater than the one you hold for that market — you do not need `messageId` there. See each
  channel's reference.
</Note>

## History

Each namespace with history enabled maintains a server-side log of recent publications. You can fetch
this directly with `sub.history()` — useful for seeding initial state or auditing recent activity
without a separate REST call.

<CodeGroup>
  ```javascript JavaScript theme={null}
  const resp = await sub.history({ limit: 50 });
  for (const pub of resp.publications) {
    applyUpdate(pub.data);
  }
  ```

  ```python Python theme={null}
  resp = await sub.history(limit=50)
  for pub in resp.publications:
      apply_update(pub.data)
  ```
</CodeGroup>

### Parameters

| Parameter | Type | Description |
| - | - | - |
| `limit` | `number` | Max publications to return. `0` returns only the current stream position (no publications). Omit for all history up to the server cap. |
| `since` | `{ offset: number, epoch: string }` | Start from a known stream position. Useful for paginating through history. Omit to start from the beginning (forward) or end (reverse). |
| `reverse` | `boolean` | `false` (default) = oldest first. `true` = newest first. |

The response contains a `publications` array and the current stream `offset` and `epoch`. Each entry
has `data`, `offset`, `tags`, and `info` fields — access the payload via `pub.data`, the same as
`ctx.data` in a live publication event.

### Limits

History fetches are bounded by the per-namespace caps in
[Channel and limit reference](/developers/realtime-reference#history-and-recovery-per-namespace) and the
global limit of 1,000 items per request. Calling `sub.history()` on a channel with no history
enabled returns error code `108`. `orderbook_v3` keeps one message as a cache rather than a log, so
the client is not granted history there.

For the snapshot-plus-subscribe seed pattern, see
[Initialization → Snapshot + subscribe pattern](/developers/realtime-initialization#snapshot--subscribe-pattern).

***

<Card title="Real-time data →" icon="bolt" href="/developers/realtime-overview">
  The guide with full worked examples, channel reference, and common failures.
</Card>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.