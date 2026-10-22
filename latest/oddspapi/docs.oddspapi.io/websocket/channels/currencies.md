> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Currencies Channel - Exchange Rates & Crypto Prices

> Stream realtime currency exchange rates via WebSocket. Fiat and cryptocurrency pricing data including BTC, ETH, and major tokens for odds conversion.

## What it streams

Currency/ticker exchange values (vs a reference currency) for fiat and crypto.

This is a **global channel** — it is not scoped to any fixture or future.

***

## Routing

* Channel: `currencies` (global)
* Filters: ❌ ignored
* Access: allowed for all keys (unless specifically disabled)

***

## Payload structure

Each message contains a flat list of currency values:

| Field | Type | Description |
| - | - | - |
| `currency` | `string` | Symbol of the currency/token (e.g. `BTC`, `USD`, `ADA`) |
| `currencyValue` | `number` | Current exchange rate (float) |
| `updatedAt` | `string (ISO)` | Time this value was last refreshed |

***

## Example message

```json theme={null}
{
  "channel": "currencies",
  "type": "UPDATE",
  "payload": {
    "BTC": {
      "currency": "BTC",
      "currencyValue": 0.00001141,
      "updatedAt": "2025-12-28T18:38:02+00:00"
    }
  },
  "ts": 1766939809876,
  "entryId": "1766939809876-700"
}
```

***

## Notes

* All values are relative to the system's reference currency: USD


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.