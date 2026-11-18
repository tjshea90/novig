> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Partner API

> Create and manage SX Bet accounts for your own users, from your platform.

The Partner API lets an approved platform create SX Bet accounts for its own users. Each user you
create is a full SX Bet account with its own wallet, balance, positions and history. You hold that
user's API key and trade for them through the standard API. Your users never sign up on sx.bet.

## Why create your own users

If you run one SX Bet account for your whole platform, every user's bets land in one balance and
one history, and you tell them apart with [`externalUserId`](https://docs.sx.bet/developers/external-user-id). See
[Account models](https://docs.sx.bet/developers/account-models.md).

The Partner API gives each user their own account instead:

* **Per-user balances and risk.** Funds, open orders, positions and settlements are separate for
  every user, so one user's activity never touches another's.
* **Per-user history.** Orders, fills, bets and the ledger come back per account, with no
  attribution layer to maintain.
* **You stay in control.** You choose which permissions each user's API key gets, and you keep the
  key. SX Bet never sees a user's private key.
* **No separate sign-up.** Users are created from your backend in one call. They don't need an
  sx.bet login.
* **Externally owned accounts.** The signature must recover to the user's address, from
  `eth_signTypedData_v4` on a standard externally owned account. Contract-wallet signatures
  (EIP-1271) are not accepted.
* **Server-side integration.** Anything done on behalf of the user is done server side with the API key, which gives you complete control over
  your front-end

## How it works

There are two kinds of key.

| Key | Who holds it | What it can do |
| - | - | - |
| **Partner key** | You. Issued by SX Bet on your own sx.bet account. | Call the Partner API: create users and list them. It cannot place orders or read any account. |
| **User key** | You, one per user. Returned when you create the user. | Call the standard API as that user, with the permissions you chose. |

> [!NOTE]
>
> Only new addresses can be added. If an address already has an SX Bet account, create-user refuses
> it.

## Becoming a partner

**1. Register on sx.bet and reach out**

Register an account on [sx.bet](https://sx.bet), then email
[business@sx.bet](mailto:business@sx.bet) with your account's **signing EOA address**, your
platform, what you're building and roughly how many users you expect. The signing EOA is the
wallet you use to sign in to sx.bet; your proxy wallet is a separate address used to hold betting
funds. Each person or system that needs a partner key registers their own account and sends us
its signing EOA address. We'll get back to you.

**2. We set you up**

We create your partner account and send you your **partner ID** and your **partner keys**, each
issued on the account you registered. Partner keys are per environment, so you get separate keys
for [testnet and mainnet](https://docs.sx.bet/developers/testnet-and-mainnet.md). For testnet keys, register on
[toronto.sx.bet](https://toronto.sx.bet) too and send us that account's signing EOA address.

**3. Create users**

Create a user for each new wallet, as described below.

**4. Trade for your users**

Use each user's key with the standard API: deploy their proxy wallet, fund it and place orders.

## Create a user

Send every Partner API request with your partner key in the `x-sx-api-key` header.

**1. Get the statement to sign**

Call [`GET /partner/attestation`](https://docs.sx.bet/api-reference/get-partner-attestation) with the user's wallet
address. It returns EIP-712 typed data with your partner ID, the address and the environment's
chain ID filled in.

**2. Sign it with the user's wallet**

Sign the typed data with the wallet that will own the account (`eth_signTypedData_v4`).

**3. Create the user**

Call [`POST /partner/users`](https://docs.sx.bet/api-reference/post-partner-users) with the address, the user's
email, the permissions for their API key and the signature. The response includes the user's
API key.

**4. Store the user key**

The key is shown **once**. Store it securely before doing anything else. It cannot be fetched
again.

```ts theme={null}
import { privateKeyToAccount } from "viem/accounts";

const API = "https://api.toronto.sx.bet";
const partnerKey = process.env.SX_PARTNER_KEY!;
const wallet = privateKeyToAccount(process.env.USER_PRIVATE_KEY as `0x${string}`);

// 1. The statement to sign
const attestation = await fetch(
  `${API}/partner/attestation?address=${wallet.address}`,
  { headers: { "x-sx-api-key": partnerKey } }
).then((r) => r.json());
const { domain, types, primaryType, message } = attestation.data;

// 2. The user's wallet signs it
const signature = await wallet.signTypedData({
  domain,
  types,
  primaryType,
  message: { ...message, partnerId: BigInt(message.partnerId) },
});

// 3. Create the user
const created = await fetch(`${API}/partner/users`, {
  method: "POST",
  headers: { "x-sx-api-key": partnerKey, "content-type": "application/json" },
  body: JSON.stringify({
    address: wallet.address,
    userEmail: "trader+1324@yourplatform.com",
    apiKeyPermissions: {
      orders: ["read", "create", "cancel"],
      account: ["read"],
      wallet: ["read", "admin"],
    },
    signature,
  }),
}).then((r) => r.json());

// 4. Store this now: it is shown only once
const userKey = created.data.apiKey.key;
```

Choose the user key's permissions from the scopes in
[API key permissions](https://docs.sx.bet/developers/api-key-permissions.md). Grant only what you need, but include
`wallet: admin` if you will deploy the user's proxy wallet with their key.

## After a user is created

The user's account is ready, and their [proxy wallet](https://docs.sx.bet/developers/accounts.md) is being deployed. From
here on, use the **user key**, not your partner key.

1. **Wait for the proxy wallet.** The create response reports the deployment status in
   `proxyDeployment.status`:
   * `SUCCESS`: deployed, carry on.
   * `PENDING`: a deployment is running. Don't start another; poll
     [`GET /partner/users`](https://docs.sx.bet/api-reference/get-partner-users) until the user's `proxyDeployed` is
     `true`.
   * `FAILED` or `NOT_STARTED`: call
     [`POST /user/deploy-proxy`](https://docs.sx.bet/api-reference/post-user-deploy-proxy) with the user key, then
     poll the same way.
2. **Fund it.** Send funds to the user's `proxyWalletAddress` only after deployment completes. See
   [Funding](https://docs.sx.bet/developers/funding.md).
3. **Trade.** Place orders with [`POST /orders-v3`](https://docs.sx.bet/api-reference/post-orders-v3). Each order is
   signed by the user's wallet, as for any account.

## View your users

[`GET /partner/users`](https://docs.sx.bet/api-reference/get-partner-users) lists the users you have created, newest
first, with each user's wallet, proxy wallet, email, status and whether their proxy wallet is
deployed. Filter by `address` to look up one user.

A user's `status` is `SUSPENDED` while SX Bet has suspended the account, and `ACTIVE` otherwise. A
suspended user cannot place orders.

## Rate limits

Each partner key has its own rate limit, separate from your other partner keys and from your users'
keys. By default a partner key can create up to **100 users per 60 seconds**, and listing users has
its own, separate limit.

User keys follow the standard [rate limits](https://docs.sx.bet/developers/rate-limits.md), counted per user.

## Managing users

Today, to suspend a user, [contact us](mailto:business@sx.bet) and we make the change.

## Related

> [card] [Create a user](https://docs.sx.bet/api-reference/post-partner-users.md)
>
> `POST /partner/users`: request, response and errors.

> [card] [List your users](https://docs.sx.bet/api-reference/get-partner-users.md)
>
> `GET /partner/users`: filters and pagination.

> [card] [Get the attestation](https://docs.sx.bet/api-reference/get-partner-attestation.md)
>
> `GET /partner/attestation`: the typed data a wallet signs.

> [card] [API key permissions](https://docs.sx.bet/developers/api-key-permissions.md)
>
> Scopes you can give your users' keys.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.