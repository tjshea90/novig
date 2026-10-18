> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Heartbeat

> Arm a dead-man switch that cancels your open orders if your process dies.

[`POST /heartbeat/v3`](/api-reference/post-heartbeat-v3) registers a timer that is meant to cancel
your resting orders if you do not send a heartbeat in time by calling the same endpoint

## Arming and refreshing

Arming and refreshing are the same request. The body is one field, `timeoutSeconds`, from `0` to `3600`.

<CodeGroup>
  ```js JavaScript theme={null}
  const beat = async () => {
    const res = await fetch(`${API}/heartbeat/v3`, {
      method: "POST",
      headers: { ...auth, "content-type": "application/json" },
      body: JSON.stringify({ timeoutSeconds: 60 }),
    });
    if (!res.ok) throw new Error(`heartbeat failed: HTTP ${res.status}`);
    return (await res.json()).data.expiresAt;   // 2026-08-06T14:31:07.482Z
  };

  await beat();
  setInterval(() => beat().catch(console.error), 20_000);
  ```

  ```python Python theme={null}
  def beat():
      res = requests.post(f"{API}/heartbeat/v3", headers=auth, json={"timeoutSeconds": 60})
      res.raise_for_status()
      return res.json()["data"]["expiresAt"]   # 2026-08-06T14:31:07.482Z
  ```
</CodeGroup>

Refresh inside the timeout window

## When it lapses

Every open order is cancelled for your account. It has the same effect as
[`DELETE /orders-v3/all`](/api-reference/delete-orders-v3-all). The cancels are asynchronous, so orders will be
cancelled shortly after with `inactiveReason: "HEARTBEAT_TIMEOUT"`.

## Stop the heartbeat

To clear the heartbeat, call
[`POST /heartbeat/v3`](/api-reference/post-heartbeat-v3) again with `timeoutSeconds: 0`. That
deactivates the timer.

## Related

<CardGroup cols={2}>
  <Card title="Set heartbeat" icon="heart-pulse" href="/api-reference/post-heartbeat-v3">
    The `POST /heartbeat/v3` endpoint reference.
  </Card>

  <Card title="Cancelling orders" icon="ban" href="/developers/cancelling-orders">
    The shutdown path that confirms each cancel.
  </Card>

  <Card title="Order lifecycle" icon="arrow-progress" href="/developers/order-lifecycle">
    Every way an order leaves the book, and how to distinguish them.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.