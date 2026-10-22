> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Fund Your Account

> Deposit, withdraw, and transfer pUSD collateral for Perps trading

Fund the Perps account with pUSD before placing orders. Deposits move pUSD from
the user's Polymarket wallet into the Perps account. Withdrawals move available
pUSD back to the authenticated wallet. Internal transfers move pUSD between two
Perps accounts, including between accounts in the same account group.

## Deposit Collateral

Deposit pUSD when the account needs collateral for opening or maintaining Perps
positions.

**TypeScript**

**1. Create a Secure Client**

Create a `SecureClient` for the wallet that will fund the Perps account. If you
already have a Polymarket wallet, pass it as `wallet` and include a Relayer API
key so the SDK can submit gasless transactions. If you are creating a wallet
programmatically, use a Builder API key so the SDK can create the Deposit Wallet
for that signer.

```ts Existing Account theme={null}
import { createSecureClient, relayerApiKey } from "@polymarket/client";
import { privateKey } from "@polymarket/client/viem";

const client = await createSecureClient({
  wallet: process.env.POLYMARKET_WALLET_ADDRESS!,
  signer: privateKey(process.env.PRIVATE_KEY!),
  apiKey: relayerApiKey({
    key: process.env.RELAYER_API_KEY!,
    address: process.env.RELAYER_API_KEY_ADDRESS!,
  }),
});
```

```ts New Programmatic Wallet theme={null}
import { createSecureClient } from "@polymarket/client";
import { builderApiKey } from "@polymarket/client/node";
import { privateKey } from "@polymarket/client/viem";

const client = await createSecureClient({
  signer: privateKey(process.env.PRIVATE_KEY!),
  apiKey: builderApiKey({
    key: process.env.BUILDER_API_KEY!,
    secret: process.env.BUILDER_SECRET!,
    passphrase: process.env.BUILDER_PASSPHRASE!,
  }),
});
```

**2. Set Up Deposit Approvals**

Set up the approvals required for Perps collateral deposits. The SDK skips work
that is already complete.

```ts theme={null}
await client.setupTradingApprovals();
```

**3. Deposit Collateral**

Deposit pUSD from the user's Polymarket wallet into the Perps account. Make sure
the wallet has pUSD before depositing. The minimum Perps deposit is 10 pUSD.
Amounts use raw pUSD base units, so 10 pUSD is `10_000_000n`.

```ts theme={null}
const deposit = await client.depositToPerps({
  amount: 10_000_000n,
});

const receipt = await deposit.wait();
// receipt.transactionHash: TxHash
```

`deposit.wait()` confirms that the chain transaction settled. Perps may take a
moment to credit the account after that.

**4. Verify the Deposit**

Open a Perps session and read account state after the deposit settles.

```ts theme={null}
const session = await client.openPerpsSession();

try {
  const portfolio = await session.fetchPortfolio();
  const pages = session.listDeposits();
  const deposits = await pages.firstPage();
} finally {
  await session.close();
}
```

Use `portfolio.withdrawable` to check collateral available for withdrawal and
`deposits.items` to reconcile deposit history. The `withdrawable` value already
accounts for the [10% notional reserve](/perps/learn-about-trading/margin.md#withdrawal-margin-requirements).

**Python**

**1. Create a Secure Client**

Create an `AsyncSecureClient` for the wallet that will fund the Perps account.
If you already have a Polymarket wallet, pass it as `wallet` and include a
Relayer API key so the SDK can submit gasless transactions. If you are creating a
wallet programmatically, use a Builder API key so the SDK can create the Deposit
Wallet for that signer.

```python Existing Account theme={null}
import os

from polymarket import AsyncSecureClient, RelayerApiKey

client = await AsyncSecureClient.create(
    private_key=os.environ["PRIVATE_KEY"],
    wallet=os.environ["POLYMARKET_WALLET_ADDRESS"],
    api_key=RelayerApiKey(
        key=os.environ["RELAYER_API_KEY"],
        address=os.environ["RELAYER_API_KEY_ADDRESS"],
    ),
)
```

```python New Programmatic Wallet theme={null}
import os

from polymarket import AsyncSecureClient, BuilderApiKey

client = await AsyncSecureClient.create(
    private_key=os.environ["PRIVATE_KEY"],
    api_key=BuilderApiKey(
        key=os.environ["BUILDER_API_KEY"],
        secret=os.environ["BUILDER_SECRET"],
        passphrase=os.environ["BUILDER_PASSPHRASE"],
    ),
)
```

**2. Set Up Deposit Approvals**

Set up the approvals required for Perps collateral deposits. The SDK skips work
that is already complete.

```python theme={null}
await client.setup_trading_approvals()
```

**3. Deposit Collateral**

Deposit pUSD from the user's Polymarket wallet into the Perps account. Make sure
the wallet has pUSD before depositing. The minimum Perps deposit is 10 pUSD.
Amounts use raw pUSD base units, so 10 pUSD is `10_000_000`.

```python theme={null}
deposit = await client.deposit_to_perps(amount=10_000_000)

receipt = await deposit.wait()
# receipt.transaction_hash: TransactionHash
```

`deposit.wait()` confirms that the chain transaction settled. Perps may take a
moment to credit the account after that.

**4. Verify the Deposit**

Open a Perps session and read account state after the deposit settles.

```python theme={null}
session = await client.open_perps_session()

try:
    portfolio = await session.fetch_portfolio()
    pages = session.list_deposits()
    deposits = await pages.first_page()
finally:
    await session.close()
```

Use `portfolio.withdrawable` to check collateral available for withdrawal and
`deposits.items` to reconcile deposit history. The `withdrawable` value already
accounts for the [10% notional reserve](/perps/learn-about-trading/margin.md#withdrawal-margin-requirements).

**API**

**1. Check Deposit Approval**

Before depositing, the Polymarket wallet must approve the Perps deposit contract
to spend pUSD. If the approval is already in place, skip the approval call.

```solidity Approval Call theme={null}
pUSD.approve(PerpsDepositContract, maxUint256)
```

Use these contract addresses when building the approval call.

| Contract | Address |
| - | - |
| pUSD collateral token | `0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB` |
| Perps deposit contract | `0xDCa4af75705dbB50f62437045afF9921947917d2` |

> [!NOTE]
>
> The following steps show the Deposit Wallet batch path. If you are trading
> with a Safe or Poly Proxy wallet, use an SDK that handles the wallet-specific
> transaction flow for you.

**2. Build the Deposit Call**

Create the Perps deposit call. Deposit amounts use pUSD base units, so 10 pUSD
is `10000000`.

```solidity theme={null}
function deposit(address token, uint256 amount, address to);
```

Encode the deposit calldata with these arguments.

| Argument | Value |
| - | - |
| `token` | `0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB` |
| `amount` | `10000000` |
| `to` | Signer address for the Polymarket account. |

Build the final ordered call list. Include the approval call first only when
approval is needed.

```json Without Approval theme={null}
[
  {
    "target": "0xDCa4af75705dbB50f62437045afF9921947917d2",
    "value": "0",
    "data": "<deposit_calldata>"
  }
]
```

```json With Approval theme={null}
[
  {
    "target": "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB",
    "value": "0",
    "data": "<approve_calldata>"
  },
  {
    "target": "0xDCa4af75705dbB50f62437045afF9921947917d2",
    "value": "0",
    "data": "<deposit_calldata>"
  }
]
```

**3. Fetch a Relayer Nonce**

Fetch a fresh `WALLET` nonce before submitting the Deposit Wallet batch.

```bash theme={null}
curl -G "https://relayer-v2.polymarket.com/v1/account/transactions/params" \
  -H "RELAYER_API_KEY: $RELAYER_API_KEY" \
  -H "RELAYER_API_KEY_ADDRESS: $RELAYER_API_KEY_ADDRESS" \
  --data-urlencode "address=<polymarket_account_signer_address>" \
  --data-urlencode "type=WALLET"
```

The response includes the nonce to sign with the batch.

```json theme={null}
{
  "address": "<polymarket_account_signer_address>",
  "nonce": "<wallet_nonce>"
}
```

**4. Build the Deposit Wallet Batch**

Build the EIP-712 `Batch` typed data for the Deposit Wallet. Use the final
ordered call list from the previous step, and omit the approval call when
allowance is already sufficient. Set `deadline` to a Unix timestamp in seconds
after which the relayer should reject the batch.

```json theme={null}
{
  "domain": {
    "name": "DepositWallet",
    "version": "1",
    "chainId": 137,
    "verifyingContract": "<polymarket_wallet_address>"
  },
  "primaryType": "Batch",
  "types": {
    "Call": [
      { "name": "target", "type": "address" },
      { "name": "value", "type": "uint256" },
      { "name": "data", "type": "bytes" }
    ],
    "Batch": [
      { "name": "wallet", "type": "address" },
      { "name": "nonce", "type": "uint256" },
      { "name": "deadline", "type": "uint256" },
      { "name": "calls", "type": "Call[]" }
    ]
  },
  "message": {
    "wallet": "<polymarket_wallet_address>",
    "nonce": "<wallet_nonce>",
    "deadline": "<unix_seconds>",
    "calls": [
      {
        "target": "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB",
        "value": "0",
        "data": "<approve_calldata>"
      },
      {
        "target": "0xDCa4af75705dbB50f62437045afF9921947917d2",
        "value": "0",
        "data": "<deposit_calldata>"
      }
    ]
  }
}
```

Sign this typed data with the signer for the Polymarket account.

**5. Submit the Deposit Transaction**

Submit the signed batch to the Relayer API. Use the same ordered call list you
signed in the previous step.

```bash theme={null}
curl -X POST "https://relayer-v2.polymarket.com/submit" \
  -H "Content-Type: application/json" \
  -H "RELAYER_API_KEY: $RELAYER_API_KEY" \
  -H "RELAYER_API_KEY_ADDRESS: $RELAYER_API_KEY_ADDRESS" \
  -d '{
    "type": "WALLET",
    "from": "<polymarket_account_signer_address>",
    "to": "0x00000000000Fb5C9ADea0298D729A0CB3823Cc07",
    "nonce": "<wallet_nonce>",
    "signature": "<wallet_batch_signature>",
    "metadata": "Deposit pUSD to Perps",
    "depositWalletParams": {
      "depositWallet": "<polymarket_wallet_address>",
      "deadline": "<unix_seconds>",
      "calls": [
        {
          "target": "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB",
          "value": "0",
          "data": "<approve_calldata>"
        },
        {
          "target": "0xDCa4af75705dbB50f62437045afF9921947917d2",
          "value": "0",
          "data": "<deposit_calldata>"
        }
      ]
    }
  }'
```

The response includes the relayer transaction ID.

```json theme={null}
{
  "transactionID": "<transaction_id>",
  "state": "STATE_NEW"
}
```

**6. Poll the Deposit Transaction**

Poll the relayer transaction until it reaches `STATE_CONFIRMED` before relying
on the deposited collateral.

```bash theme={null}
curl "https://relayer-v2.polymarket.com/v1/account/transactions/<transaction_id>" \
  -H "RELAYER_API_KEY: $RELAYER_API_KEY" \
  -H "RELAYER_API_KEY_ADDRESS: $RELAYER_API_KEY_ADDRESS"
```

```json theme={null}
{
  "transaction_id": "<transaction_id>",
  "transaction_hash": "<transaction_hash>",
  "state": "STATE_CONFIRMED",
  "error_msg": null
}
```

Perps may take a moment to credit the account after the onchain transaction
settles. Treat `STATE_FAILED` and `STATE_INVALID` as terminal failures.

## Withdraw Collateral

Withdraw pUSD when the account has available collateral that should return to the
authenticated wallet.

Withdrawals must satisfy the [withdrawal margin requirements](/perps/learn-about-trading/margin.md#withdrawal-margin-requirements),
including the 10% reserve against open-position notional. Collateral available
for trading can therefore exceed collateral available for withdrawal.

**TypeScript**

Request a withdrawal to the authenticated wallet.
Amounts use raw pUSD base units, so 10 pUSD is `10_000_000n`.

```ts theme={null}
const withdrawalId = await client.withdrawFromPerps({
  amount: 10_000_000n,
});
```

The SDK signs the withdrawal request with the Polymarket account signer and
returns the Perps withdrawal ID.

To track the withdrawal, open a Perps session and list withdrawals.

```ts theme={null}
const session = await client.openPerpsSession();

try {
  const pages = session.listWithdrawals();
  const withdrawals = await pages.firstPage();
} finally {
  await session.close();
}
```

To narrow the results to a single state, pass a `withdrawalStatus` filter with
a `PerpsKnownWithdrawalStatus` member. The filter accepts one status per
request. For example, list the withdrawals that are still pending:

```ts theme={null}
import { PerpsKnownWithdrawalStatus } from "@polymarket/client";

const pages = session.listWithdrawals({
  withdrawalStatus: PerpsKnownWithdrawalStatus.Pending,
});

for await (const page of pages) {
  // page.items: PerpsWithdrawal[]
}
```

For what each status means, see
[Withdrawals](/perps/account-management.md#withdrawals).

For more details on authenticated sessions, see [Authenticated
Sessions](/perps/authenticated-sessions.md).

**Python**

Request a withdrawal to the authenticated wallet. Amounts use raw pUSD base
units, so 10 pUSD is `10_000_000`.

```python theme={null}
withdrawal_id = await client.withdraw_from_perps(amount=10_000_000)
```

The SDK signs the withdrawal request with the Polymarket account signer and
returns the Perps withdrawal ID.

To track the withdrawal, open a Perps session and list withdrawals.

```python theme={null}
session = await client.open_perps_session()

try:
    pages = session.list_withdrawals()
    withdrawals = await pages.first_page()
finally:
    await session.close()
```

To narrow the results to a single state, pass a `withdrawal_status` filter.
The filter accepts one status per request: `"pending"`, `"confirmed"`,
`"removed"`, or `"failed"`. For example, list the withdrawals that are still
pending:

```python theme={null}
pages = session.list_withdrawals(withdrawal_status="pending")

async for page in pages:
    # page.items: tuple[PerpsWithdrawal, ...]
    pass
```

For what each status means, see
[Withdrawals](/perps/account-management.md#withdrawals).

For more details on authenticated sessions, see [Authenticated
Sessions](/perps/authenticated-sessions.md).

**API**

**1. Build the Withdrawal Operation**

Create a `withdraw` operation with the account signer, pUSD token, raw token
amount, and destination wallet. For withdrawals, `amount` is the raw pUSD token
amount, so 10 pUSD is `10000000`.

```json theme={null}
{
  "type": "withdraw",
  "args": {
    "account": "<polymarket_account_signer_address>",
    "token": "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB",
    "amount": "10000000",
    "to": "<polymarket_wallet_address>"
  }
}
```

| Field | Value |
| - | - |
| `account` | Signer address for the Polymarket account. |
| `token` | pUSD collateral token address. |
| `amount` | Raw pUSD token amount. |
| `to` | Wallet that receives the withdrawal. |

**2. Create Withdrawal Typed Data**

The withdrawal signature uses EIP-712 typed data with `Withdraw` as the primary
type. Use the same `account`, `token`, `amount`, and `to` values from the
withdrawal operation.

For withdrawals, `ts` is a Unix timestamp in seconds because the onchain contract
validates it against `block.timestamp`. It must match the `ts` value in the
request body.

```json theme={null}
{
  "domain": {
    "name": "Polymarket",
    "version": "1",
    "chainId": 137,
    "verifyingContract": "0xDCa4af75705dbB50f62437045afF9921947917d2"
  },
  "primaryType": "Withdraw",
  "types": {
    "Withdraw": [
      { "name": "account", "type": "address" },
      { "name": "token", "type": "address" },
      { "name": "amount", "type": "uint256" },
      { "name": "fee", "type": "uint256" },
      { "name": "to", "type": "address" },
      { "name": "salt", "type": "uint64" },
      { "name": "ts", "type": "uint64" }
    ]
  },
  "message": {
    "account": "<polymarket_account_signer_address>",
    "token": "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB",
    "amount": "10000000",
    "fee": "0",
    "to": "<polymarket_wallet_address>",
    "salt": 555555555,
    "ts": 1767000014
  }
}
```

| Field | Value |
| - | - |
| `salt` | Random integer generated for this signed request. |
| `ts` | Current Unix timestamp in seconds, not milliseconds. |

**3. Sign Withdrawal Typed Data**

Sign the typed data with the Polymarket account signer. The example below uses
Viem.

```ts Viem theme={null}
import { privateKeyToAccount } from "viem/accounts";

const account = privateKeyToAccount("<polymarket_account_signer_private_key>");

const signature = await account.signTypedData({
  domain: {
    name: "Polymarket",
    version: "1",
    chainId: 137,
    verifyingContract: "0xDCa4af75705dbB50f62437045afF9921947917d2",
  },
  primaryType: "Withdraw",
  types: {
    Withdraw: [
      { name: "account", type: "address" },
      { name: "token", type: "address" },
      { name: "amount", type: "uint256" },
      { name: "fee", type: "uint256" },
      { name: "to", type: "address" },
      { name: "salt", type: "uint64" },
      { name: "ts", type: "uint64" },
    ],
  },
  message: {
    account: "<polymarket_account_signer_address>",
    token: "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB",
    amount: 10000000n,
    fee: 0n,
    to: "<polymarket_wallet_address>",
    salt: 555555555n,
    ts: 1767000014n,
  },
});
```

**4. Submit the Withdrawal**

Submit the signed withdrawal request to `POST /v1/account/withdraw`. Use the
same operation values, `salt`, and `ts` from the typed data.

```bash theme={null}
curl -X POST "https://api.perpetuals.polymarket.com/v1/account/withdraw" \
  -H "content-type: application/json" \
  -d '{
    "op": {
      "type": "withdraw",
      "args": {
        "account": "<polymarket_account_signer_address>",
        "token": "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB",
        "amount": "10000000",
        "to": "<polymarket_wallet_address>"
      }
    },
    "sig": "<signature>",
    "salt": 555555555,
    "ts": 1767000014
  }'
```

The response indicates whether the withdrawal was accepted.

```json Success theme={null}
{
  "status": "ok",
  "withdraw_id": 1234567890
}
```

```json Failure theme={null}
{
  "status": "err",
  "withdraw_id": 1234567890,
  "error": "insufficient_balance"
}
```

**5. Track the Withdrawal**

Use the returned `withdraw_id` to match the withdrawal against history results.

```bash theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/withdrawals" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "withdrawal_status=pending"
```

The `withdrawal_status` filter accepts `pending`, `confirmed`, `removed`, or
`failed`.

For more details on proxy credentials and private account-read headers, see
[Authenticated Sessions](/perps/authenticated-sessions.md).

## Transfer Between Your Accounts

Move pUSD between two Perps accounts with an internal transfer. The transfer
settles on the exchange ledger, with no onchain transaction and no fee.

The source owner can transfer to another Perps account. Transfers are limited to
withdrawable collateral after accounting for pending withdrawals and transfers.

**TypeScript**

Use `client.transferPerpsCollateral()` on the owner-signed `SecureClient` from
[Set Up Perps Access](/perps/authenticated-sessions.md#set-up-perps-access).

```ts theme={null}
const transferId = await client.transferPerpsCollateral({
  recipient: "0x9965507D1a55bcC2695C58ba16FB37d819B0A4dc",
  amount: "250.50",
  label: "rebalance-001",
});
console.log(transferId);
```

The result is a `PerpsInternalTransferId`. `amount` is decimal pUSD, and its exact
string is signed unchanged. Choose a recipient distinct from the owner's address.
`label` accepts 1 to 64 UTF-8 bytes and helps identify the transfer in history.
The SDK signs with the owner and attempts the submission once.

**Python**

Use the API tab to submit an internal transfer. To read settled transfers with
Python, use the history workflow in **Reconcile a Transfer** below.

**API**

Sign an internal transfer with the source account owner. When grouped-account
transfers are enabled for your deployment, a registered trading key can also sign:

| Signer | When it can sign |
| - | - |
| Owner key of the source account | Always, to any destination Perps account. |
| Trading key (proxy) registered to the source account | When enabled and the source and destination belong to the same [account group](/perps/learn-about-trading/self-trade-prevention.md#account-groups). |

The trading-key path lets a market maker running several grouped accounts
rebalance collateral from the key its trading system already holds, without
bringing the owner key online. Polymarket assigns account groups per entity; to
group your accounts, contact Polymarket with the list of accounts. A trading key
must be registered to the **source** account. A key registered to a different
member of the group does not qualify, and ungrouped accounts cannot use the
trading-key path.

Trading keys can only move collateral between members of the same group. They
can never withdraw; withdrawals always require the owner key.

**1. Build the Transfer Operation**

Create an `internalTransfer` operation naming the source account, the pUSD
token, the amount and the destination account.

```json theme={null}
{
  "type": "internalTransfer",
  "args": {
    "account": "<source_account_address>",
    "token": "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB",
    "amount": "250.5",
    "to": "<destination_account_address>"
  }
}
```

| Field | Value |
| - | - |
| `account` | Address of the source Perps account (its owner address). |
| `token` | pUSD collateral token address. |
| `amount` | pUSD amount as a decimal string, for example `"250.5"`. |
| `to` | Address of the destination Perps account (its owner address). |

> [!WARNING]
>
> Internal transfer amounts are **decimal pUSD**, not raw token base units.
> `"250.5"` moves 250.5 pUSD. Withdrawals are different: their `amount` is raw
> base units.

**2. Create the Operation Hash**

Internal transfers use the same `Op` signing scheme as trading operations. Build
the compact operation, MessagePack-encode it, and hash the encoded bytes with
`keccak256`.

```ts theme={null}
["internalTransfer", [account, token, amount, to]];
```

Use the exact strings you send in the request body, including address casing
and the amount string. Any difference changes the hash and the signature fails.

```ts theme={null}
import { encode } from "@msgpack/msgpack";
import { keccak256 } from "viem";

const signableOperation = [
  "internalTransfer",
  [
    "<source_account_address>",
    "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB",
    "250.5",
    "<destination_account_address>",
  ],
] as const;

const opHash = keccak256(encode(signableOperation));
```

**3. Create Transfer Typed Data**

Create an EIP-712 `Op` typed-data payload with the operation hash.

```json theme={null}
{
  "domain": {
    "name": "Polymarket",
    "version": "1",
    "chainId": 137
  },
  "primaryType": "Op",
  "types": {
    "Op": [
      { "name": "data", "type": "bytes32" },
      { "name": "salt", "type": "uint64" },
      { "name": "ts", "type": "uint64" }
    ]
  },
  "message": {
    "data": "<op_hash>",
    "salt": 345678901,
    "ts": 1767000020000
  }
}
```

| Field | Value |
| - | - |
| `data` | Operation hash from the previous step. |
| `salt` | Random integer generated for this signed request. |
| `ts` | Current Unix timestamp in milliseconds. |

**4. Sign Transfer Typed Data**

Sign with the source account's trading key for a transfer between grouped
accounts, or with the source account's owner key for any transfer.

```ts Viem theme={null}
import { privateKeyToAccount } from "viem/accounts";

const account = privateKeyToAccount("<proxy_private_key>");

const signature = await account.signTypedData({
  domain: {
    name: "Polymarket",
    version: "1",
    chainId: 137,
  },
  primaryType: "Op",
  types: {
    Op: [
      { name: "data", type: "bytes32" },
      { name: "salt", type: "uint64" },
      { name: "ts", type: "uint64" },
    ],
  },
  message: {
    data: opHash,
    salt: 345678901,
    ts: 1767000020000,
  },
});
```

**5. Submit the Transfer**

Submit the signed request to `POST /v1/account/internal-transfer`. Use the same
operation values, `salt`, and `ts` from the typed data. `label` is optional,
up to 64 bytes, and sits outside the signed operation. It is returned in both
accounts' transfer history, which helps you reconcile your own rebalancing.

```bash theme={null}
curl -X POST "https://api.perpetuals.polymarket.com/v1/account/internal-transfer" \
  -H "content-type: application/json" \
  -d '{
    "op": {
      "type": "internalTransfer",
      "args": {
        "account": "<source_account_address>",
        "token": "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB",
        "amount": "250.5",
        "to": "<destination_account_address>"
      }
    },
    "sig": "<signature>",
    "salt": 345678901,
    "ts": 1767000020000,
    "label": "strategy-rebalance"
  }'
```

```json Success theme={null}
{
  "status": "ok",
  "transfer_id": 5902406621022238
}
```

```json Failure theme={null}
{
  "status": "err",
  "transfer_id": 5902406621022238,
  "error": "insufficient_transfer_balance"
}
```

| Response | Meaning |
| - | - |
| `insufficient_transfer_balance` | The amount, plus pending transfers and withdrawals, exceeds the source account's withdrawable pUSD. |
| `transfer_limit_reached` | The source account already has 10 pending transfers. |
| `transfer_to_self` | The source and destination are the same account. |
| `proxy_expired` | The trading key's expiry has passed. |
| `signer_does_not_match_account` | The signer is neither the source owner nor a trading key registered to the source, or the accounts are not in the same group. |
| HTTP 400 `destination account not found` | The destination address has no Perps account. |
| HTTP 503 `service_unavailable` with `Retry-After: 1` | The gateway could not confirm fresh account-group state. Nothing was submitted, so it is safe to retry. |

Trading-key transfers can take slightly longer to accept than owner-key
transfers, because the gateway first confirms that its view of the key and the
group is current. This wait is capped at 500 ms.

If a transfer request times out and you are unsure whether it was accepted,
resend the exact original signed request. Do not generate a new signature,
salt, or timestamp. See [Errors](/perps/errors.md).

Transfers and withdrawals still pending count against the
[withdrawable amount](/perps/learn-about-trading/margin.md#withdrawal-margin-requirements).
Each source account can have up to 10 transfers waiting to be processed.

If submission times out or returns a server error, the transfer may still have
completed. Reconcile settled history before considering another submission.
Labels help match records; they do not make transfers idempotent. A missing
record does not prove failure, so do not automatically submit a new transfer.

### Reconcile a Transfer

Use authenticated account history to locate the collateral movement and compare
its recipient, amount, and direction with your intended transfer.

**TypeScript**

Use `session.listInternalTransfers()` with the authenticated session from
[Set Up Perps Access](/perps/authenticated-sessions.md#set-up-perps-access).

```ts theme={null}
const pages = session.listInternalTransfers();

for await (const page of pages) {
  for (const transfer of page.items) {
    if (transfer.label === "rebalance-001") {
      console.log(
        transfer.transferId,
        transfer.amount,
        transfer.direction,
        transfer.counterparty,
      );
    }
  }
}
```

Each item is a `PerpsInternalTransfer`. `amount` is a decimal string in pUSD units;
`direction` is `"in"` or `"out"` relative to this account. `counterparty` is the
other account's owner address.

The default window is the past 90 days. Set `start` and `end` in epoch milliseconds
to choose a window, or pass a returned `nextCursor` as `cursor` to resume it.
The paginator deduplicates overlapping records and raises `UnexpectedResponseError`
if a full millisecond cannot be paged without skipping records.

**Python**

Use `session.list_internal_transfers()` with the authenticated session from
[Set Up Perps Access](/perps/authenticated-sessions.md#set-up-perps-access).

```python theme={null}
pages = session.list_internal_transfers()

async for page in pages:
    for transfer in page.items:
        if transfer.label == "rebalance-001":
            print(
                transfer.transfer_id,
                transfer.amount,
                transfer.direction,
                transfer.counterparty,
            )
```

Each item is a `PerpsInternalTransfer`. `amount` is a `Decimal` in pUSD units;
`direction` is `PerpsInternalTransferDirection.IN` or
`PerpsInternalTransferDirection.OUT`. `counterparty` is the other account's owner
address.

The default window is the past 90 days. Set `start` and `end` using datetimes or
epoch milliseconds, or pass a returned `next_cursor` as `cursor` to resume its
saved bounds. The paginator deduplicates overlapping records and raises
`UnexpectedResponseError` if a full millisecond cannot be paged without skipping
records.

**API**

List settled transfers using the private account-read headers from
[Authenticated Sessions](/perps/authenticated-sessions.md).

```bash theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/internal-transfers" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "direction=out"
```

> **Output: Internal Transfer History**
>
> ```json theme={null}
> {
>   "data": [
>     {
>       "transfer_id": 4,
>       "type": "transfer",
>       "asset": "pUSD",
>       "amount": "1.23000000000000000001",
>       "direction": "out",
>       "counterparty": "0x9965507D1a55bcC2695C58ba16FB37d819B0A4dc",
>       "label": "rebalance-001",
>       "created_timestamp": 1002
>     }
>   ],
>   "more": false
> }
> ```

The response contains `data` and `more`. Each record includes `transfer_id`,
`amount`, `direction`, `counterparty`, `type`, optional `label`, and
`created_timestamp`. Compare `label` with the value sent during submission.
Use `direction=in` or `direction=out` to narrow the results; omit it for both.
`start_timestamp` and `end_timestamp` accept epoch milliseconds. The API does not
accept a pagination cursor.

An interrupted or incomplete history read does not establish whether a transfer
failed. Complete reconciliation before deciding whether to submit again.

## Review Funding History

Use deposit and withdrawal history to reconcile collateral movements after your
integration submits funding requests.

See [Authenticated Sessions](/perps/authenticated-sessions.md) for how to create an
authenticated session for private account history reads.

**TypeScript**

List deposit or withdrawal history from an authenticated Perps session.

```ts Deposits theme={null}
import type { PerpsDeposit } from "@polymarket/client";

const session = await client.openPerpsSession();

try {
  const deposits: PerpsDeposit[] = [];
  const pages = session.listDeposits();

  for await (const page of pages) {
    deposits.push(...page.items);
  }
} finally {
  await session.close();
}
```

```ts Withdrawals theme={null}
import type { PerpsWithdrawal } from "@polymarket/client";

const session = await client.openPerpsSession();

try {
  const withdrawals: PerpsWithdrawal[] = [];
  const pages = session.listWithdrawals();

  for await (const page of pages) {
    withdrawals.push(...page.items);
  }
} finally {
  await session.close();
}
```

**Python**

List deposit or withdrawal history from an authenticated Perps session.

```python Deposits theme={null}
session = await client.open_perps_session()

try:
    deposits = []
    pages = session.list_deposits()

    async for page in pages:
        deposits.extend(page.items)
finally:
    await session.close()
```

```python Withdrawals theme={null}
session = await client.open_perps_session()

try:
    withdrawals = []
    pages = session.list_withdrawals()

    async for page in pages:
        withdrawals.extend(page.items)
finally:
    await session.close()
```

**API**

List deposit history.

```bash theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/deposits" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>"
```

List withdrawal history.

```bash theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/withdrawals" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>"
```

Use the optional `deposit_status`, `withdrawal_status`, `start_timestamp`, and
`end_timestamp` query parameters when reconciling a specific window.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.