> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Maintenance

> What happens to your resting orders during a maintenance window or matching engine restart.

During maintenance or matching engine restart, **every resting order** is cancelled and reposting is
your job.

## What you receive

The cancels arrive on [`account:orders_v3_#{address}`](/api-reference/channel-orders-v3), one
message per order, with `status: "INACTIVE"` and `inactiveReason: "SYSTEM"`.

```json theme={null}
{
  "order": {
    "id": "0xbc9c298f6584e484dc23a8b4ac95755faabbb5f966b37b869b3a74acf0790fe6",
    "status": "INACTIVE",
    "inactiveReason": "SYSTEM",
    "remainingSize": "1000000"
  }
}
```


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.