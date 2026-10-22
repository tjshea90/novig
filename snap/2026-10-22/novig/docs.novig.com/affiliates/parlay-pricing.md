> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Parlay Pricing

> Query real-time Novig parlay prices within your UI

## Overview

The Parlay Pricing API allows you to retrieve Novig's parlay price for any set of outcomes directly within your platform. This enables you to show users what price they'd get on Novig before they click through.

Every example reads the host from `NOVIG_HOST`. On Production that is `https://api.novig.com`.

```bash theme={"dark"}
export NOVIG_HOST=https://api.novig.com
```

## Endpoint

`POST https://api.novig.com/nbx/v1/parlay/request/unauthenticated`

## Request Format

Send a JSON body with an array of outcome IDs:

```json theme={"dark"}
{
  "outcomes": [
    { "id": "abc123-outcome-uuid" },
    { "id": "xyz789-outcome-uuid" }
  ]
}
```

## Example Request

```bash theme={"dark"}
curl -X POST "$NOVIG_HOST/nbx/v1/parlay/request/unauthenticated" \
  -H "Content-Type: application/json" \
  -d '{
    "outcomes": [
      { "id": "abc123-outcome-uuid" },
      { "id": "xyz789-outcome-uuid" },
      { "id": "def456-outcome-uuid" }
    ]
  }'
```

## Integration with Odds Platforms

Many odds platforms (like Optic Odds) can integrate Novig parlay pricing to enhance their product offering. If you're building on an odds data platform, this endpoint provides a straightforward way to add Novig parlay pricing.

## Getting Outcome IDs

To use this endpoint, you'll need valid Novig outcome IDs. You can obtain these by:

1. **Catalog API** - List markets with `GET /v3/catalog/markets` to get outcome IDs
2. **Deeplinking** - Extract outcome IDs from Novig deeplink URLs

See the [Odds Screens](/affiliates/odds-screens.md) documentation for catalog request examples.

## Best Practices

**1. Validate Outcomes**

Ensure all outcome IDs are valid and from active markets before making the request.

**2. Handle Errors**

Implement proper error handling for invalid outcomes or unavailable markets.

**3. Cache Appropriately**

Parlay prices can change frequently - cache responses for a short duration only.

**4. Display Clearly**

When showing Novig prices alongside competitors, clearly label the source.

> [!NOTE]
>
> The parlay price returned is the unauthenticated price.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.