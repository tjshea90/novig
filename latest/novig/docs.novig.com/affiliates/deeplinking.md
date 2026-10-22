> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Deeplinking

> Direct users into the Novig app with pre-populated order slips

## Overview

Deeplinking allows you to send users directly from your platform into the Novig app with a pre-populated order slip. Novig supports both mobile and web deeplinks.

> [!NOTE]
>
> Always include your Partner ID in deeplinks to ensure proper attribution tracking.

> [!WARNING]
>
> You must detect your user's platform via their **User-Agent** and use the appropriate URL scheme:
>
> * **Desktop / mobile web** users → use `novig.com/events/...`
> * **iOS / Android native app** users → use `novigapp://events/...`
>
> Using the wrong scheme will result in a broken experience — web links won't open the native app, and `novigapp://` links won't work in a desktop browser.

## Web Deeplinking

For desktop and web users, use the `novig.com` domain.

### URL Structure

```
novig.com/events/<outcome_id>/<partner_id>/<amount>?referralCode=YOUR_CODE
```

| Parameter | Required | Description |
| - | - | - |
| `outcome_id` | Yes | The outcome ID(s) for the order slip |
| `partner_id` | Yes | Used for tracking all deeplinked trading activity |
| `amount` | No | Optional pre-filled order amount (requires partner\_id) |
| `referralCode` | No | Recommended - credits new sign-ups to your partnership |

> [!TIP]
>
> Including `?referralCode=YOUR_CODE` is highly recommended to capture sign-up credits from users who register through your deeplinks.

### Straight Order Example

Single outcome with partner ID, 10 Novig Cash, and referral code:

```
novig.com/events/abc123-event-uuid/novig/10?referralCode=YOUR_CODE
```

### Parlay Example

For parlays, separate multiple outcome IDs with commas:

```
novig.com/events/<outcome_id>,<outcome_id>,<outcome_id>/<partner_id>/<amount>?referralCode=YOUR_CODE
```

Example with three legs:

```
novig.com/events/abc123,def456,ghi789/partner_code/25?referralCode=YOUR_CODE
```

## Native App Deeplinking

For native app integrations use the custom URL scheme:

```
novigapp://events/<outcome_id>,<outcome_id>,<outcome_id>/<partner_id>?referralCode=YOUR_CODE
```

## Best Practices

> **Always Include Partner ID**
>
> Ensures all trading activity is properly attributed to your partnership.

> **Include Referral Code**
>
> Append `?referralCode=YOUR_CODE` to credit new sign-ups to your partnership.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.