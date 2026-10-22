> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Manage Positions

> Learn how to manage outcome-token inventory from creation through redemption.

Manage positions outside the order book by splitting collateral into complete
sets of outcome tokens, merging balanced tokens back into collateral, or
redeeming winning tokens after resolution.

Choose an operation based on how you want to change your position:

| Operation | Use it when |
| - | - |
| Split | You need to convert collateral into a complete set of outcome tokens. |
| Merge | You hold balanced outcome tokens and want to convert them back to collateral. |
| Redeem | A market has resolved and you want to claim collateral for winning positions. |

Choose the integration surface you will use to submit position operations.

**TypeScript**

The examples on this page assume you have a `SecureClient` configured with
either a Relayer API key or a Builder API key.

```ts Relayer API Key theme={null}
import { createSecureClient, relayerApiKey } from "@polymarket/client";
import { privateKey } from "@polymarket/client/viem";

const client = await createSecureClient({
  signer: privateKey(process.env.SIGNER_PRIVATE_KEY),
  wallet: process.env.POLYMARKET_WALLET_ADDRESS,
  apiKey: relayerApiKey({
    key: process.env.POLYMARKET_RELAYER_API_KEY!,
    address: process.env.POLYMARKET_RELAYER_API_KEY_ADDRESS!,
  }),
});
```

```ts Builder API Key theme={null}
import { createSecureClient } from "@polymarket/client";
import { builderApiKey } from "@polymarket/client/node";
import { privateKey } from "@polymarket/client/viem";

const client = await createSecureClient({
  signer: privateKey(process.env.SIGNER_PRIVATE_KEY),
  wallet: process.env.POLYMARKET_WALLET_ADDRESS,
  apiKey: builderApiKey({
    key: process.env.POLYMARKET_BUILDER_API_KEY!,
    secret: process.env.POLYMARKET_BUILDER_SECRET!,
    passphrase: process.env.POLYMARKET_BUILDER_PASSPHRASE!,
  }),
});
```

See [Wallets and
Authentication](/trading/wallets-auth.md#execute-gasless-transactions) for the
complete wallet setup and Relayer authorization flow.

**Python**

The examples on this page assume you have an `AsyncSecureClient` configured
with either a Relayer API key or a Builder API key.

```python Relayer API Key theme={null}
import os

from polymarket import AsyncSecureClient, RelayerApiKey

client = await AsyncSecureClient.create(
    private_key=os.environ["SIGNER_PRIVATE_KEY"],
    wallet=os.environ["POLYMARKET_WALLET_ADDRESS"],
    api_key=RelayerApiKey(
        key=os.environ["POLYMARKET_RELAYER_API_KEY"],
        address=os.environ["POLYMARKET_RELAYER_API_KEY_ADDRESS"],
    ),
)
```

```python Builder API Key theme={null}
import os

from polymarket import AsyncSecureClient, BuilderApiKey

client = await AsyncSecureClient.create(
    private_key=os.environ["SIGNER_PRIVATE_KEY"],
    wallet=os.environ["POLYMARKET_WALLET_ADDRESS"],
    api_key=BuilderApiKey(
        key=os.environ["POLYMARKET_BUILDER_API_KEY"],
        secret=os.environ["POLYMARKET_BUILDER_SECRET"],
        passphrase=os.environ["POLYMARKET_BUILDER_PASSPHRASE"],
    ),
)
```

The synchronous `SecureClient` provides the same methods and supports both
API key types.

See [Wallets and
Authentication](/trading/wallets-auth.md#execute-gasless-transactions) for the
complete wallet setup and Relayer authorization flow.

**API**

Use a deployed Deposit Wallet and its owner signer to submit position calls through the Relayer. Complete [wallet setup and Relayer authorization](/trading/wallets-auth.md#execute-gasless-transactions), then set the request credentials:

```bash theme={null}
RELAYER_API_KEY="<relayer_api_key>"
RELAYER_API_KEY_ADDRESS="<signer_address>"
```

Use the target for the market's position system:

| Position system | Market type | Target contract | Address |
| - | - | - | - |
| Polymarket V2 | Standard or negative risk | Router | `0x12121212006e4CD160D18e3f00711DA5c3372600` |
| CTF | Standard | CtfCollateralAdapter | `0xAdA100Db00Ca00073811820692005400218FcE1f` |
| CTF | Negative risk | NegRiskCtfCollateralAdapter | `0xadA2005600Dec949baf300f4C6120000bDB6eAab` |

Replace `<position_target_address>` with the selected address in both the signed call list and submission. For CTF, choose the adapter using the market's `negRisk` value. A market whose `version` is `"v2"` uses the Router.

**Solidity**

Call the position contract directly from the address holding the collateral or outcome tokens. Select the target for the market's position system:

| Position system | Market type | Target contract | Address |
| - | - | - | - |
| Polymarket V2 | Standard or negative risk | Router | `0x12121212006e4CD160D18e3f00711DA5c3372600` |
| CTF | Standard | CtfCollateralAdapter | `0xAdA100Db00Ca00073811820692005400218FcE1f` |
| CTF | Negative risk | NegRiskCtfCollateralAdapter | `0xadA2005600Dec949baf300f4C6120000bDB6eAab` |

Use these token contracts when granting approvals:

| Asset | Contract | Address |
| - | - | - |
| Collateral | pUSD | `0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB` |
| Polymarket V2 outcome tokens | PositionManager | `0x006F54F7f9A22e0000CC2AB60031000000ae9fEF` |
| CTF outcome tokens | Conditional Tokens | `0x4D97DCd97eC945f40cF65F87097ACe5EA0476045` |

## Split a Position

Splitting converts pUSD into a complete set of outcome tokens. Every 1 pUSD
produces 1 YES token and 1 NO token.

```text theme={null}
100 pUSD → 100 YES tokens + 100 NO tokens
```

Before splitting, ensure you have:

1. Enough pUSD in the wallet for the amount you want to split.
2. Approved the position call target for the market's position system to spend
   the wallet's pUSD.

**TypeScript**

Call `splitPosition()` on a `SecureClient`. The client identifies the
position system from the condition ID and selects the correct route.

```ts theme={null}
const transaction = await client.splitPosition({
  conditionId: market.conditionId,
  amount: 1_000_000n,
});

const outcome = await transaction.wait();

// outcome.transactionHash: TxHash
// outcome.transactionId: TransactionId | null
```

`amount` is denominated in pUSD base units, so `1_000_000n` splits 1 pUSD
into 1 YES token and 1 NO token. `wait()` returns a `TransactionOutcome`
after the transaction settles.

**Python**

Call `split_position()` on an `AsyncSecureClient`. The synchronous
`SecureClient` provides the same method. Both clients identify the position
system from the condition ID and select the correct route.

```python theme={null}
transaction = await client.split_position(
    condition_id=market.condition_id,
    amount=1_000_000,
)

outcome = await transaction.wait()

# outcome.transaction_hash: TransactionHash
# outcome.transaction_id: str | None
```

`amount` is denominated in pUSD base units, so `1_000_000` splits 1 pUSD
into 1 YES token and 1 NO token. `wait()` returns a `TransactionOutcome`
after the transaction settles.

**API**

Encode the split call, then sign and submit it as a Deposit Wallet batch.

**1. Build the Position Call**

Use the target from the API table in the setup above and the arguments for your position system:

| Argument | Polymarket V2 | CTF |
| - | - | - |
| `conditionId` | 31-byte Polymarket V2 condition ID | Original 32-byte CTF condition ID |
| `collateralToken` | — | pUSD: `0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB` |
| `parentCollectionId` | — | 32 zero bytes |
| `partition` | — | `[1, 2]` |
| `amount` | Amount in six-decimal base units | Amount in six-decimal base units |

ABI-encode the function for your market's protocol using the arguments above:

```solidity Polymarket V2 theme={null}
function split(bytes31 conditionId, uint256 amount);
```

```solidity CTF theme={null}
function splitPosition(
    address collateralToken,
    bytes32 parentCollectionId,
    bytes32 conditionId,
    uint256[] partition,
    uint256 amount
);
```

Place the resulting calldata in the wallet call list:

```json theme={null}
[
  {
    "target": "<position_target_address>",
    "value": "0",
    "data": "<operation_calldata>"
  }
]
```

Use this list as `calls` in the wallet batch.

**2. Submit the Batch**

Include the encoded call in a signed Deposit Wallet batch and submit it with the Relayer API key:

```bash theme={null}
curl -X POST "https://relayer-v2.polymarket.com/submit" \
  -H "Content-Type: application/json" \
  -H "RELAYER_API_KEY: $RELAYER_API_KEY" \
  -H "RELAYER_API_KEY_ADDRESS: $RELAYER_API_KEY_ADDRESS" \
  --data '{
  "type": "WALLET",
  "from": "<signer_address>",
  "to": "0x00000000000Fb5C9ADea0298D729A0CB3823Cc07",
  "nonce": "<wallet_nonce>",
  "signature": "<wallet_batch_signature>",
  "metadata": "Split position",
  "depositWalletParams": {
    "depositWallet": "<deposit_wallet_address>",
    "deadline": "<unix_seconds>",
    "calls": [
      {
        "target": "<position_target_address>",
        "value": "0",
        "data": "<operation_calldata>"
      }
    ]
  }
}'
```

See [Execute Gasless Transactions](/trading/wallets-auth.md#execute-gasless-transactions) for nonce creation, wallet-batch signing, and confirmation. Wait for `STATE_CONFIRMED` before using the new balances.

**Solidity**

Approve the selected position contract, then execute the split from the same address.

**1. Approve pUSD**

Call `approve` on pUSD from the address holding the collateral. Use the selected adapter or Router as `spender` and the split amount as `amount`:

```solidity theme={null}
function approve(address spender, uint256 amount) external returns (bool);
```

An existing allowance covering the amount is sufficient.

**2. Split the Position**

Use the approved target from the Solidity table in the setup above. Call the function for your position system:

```solidity Polymarket V2 theme={null}
function split(bytes31 conditionId, uint256 amount);
```

```solidity CTF theme={null}
function splitPosition(
    address collateralToken,
    bytes32 parentCollectionId,
    bytes32 conditionId,
    uint256[] partition,
    uint256 amount
);
```

| Argument | Polymarket V2 | CTF |
| - | - | - |
| `conditionId` | 31-byte Polymarket V2 condition ID | Original 32-byte CTF condition ID |
| `collateralToken` | — | pUSD: `0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB` |
| `parentCollectionId` | — | 32 zero bytes |
| `partition` | — | `[1, 2]` |
| `amount` | Amount in six-decimal base units | Amount in six-decimal base units |

## Merge Positions

Merging converts a complete set of outcome tokens back into pUSD. Every 1 YES
token and 1 NO token returns 1 pUSD.

```text theme={null}
100 YES tokens + 100 NO tokens → 100 pUSD
```

Before merging, ensure you have:

1. Equal amounts of YES and NO tokens.
2. Approved the position call target for the market's position system to
   transfer the wallet's outcome tokens.

**TypeScript**

Call `mergePositions()` on a `SecureClient`. The client identifies the
position system, selects the correct route, and checks the
wallet's mergeable balance.

```ts theme={null}
const transaction = await client.mergePositions({
  conditionId: market.conditionId,
  amount: "max",
});

const outcome = await transaction.wait();

// outcome.transactionHash: TxHash
// outcome.transactionId: TransactionId | null
```

Pass a base-unit `bigint` to merge a specific amount. `"max"` merges the
smaller of the wallet's YES and NO balances. `wait()` returns a
`TransactionOutcome` after the transaction settles.

**Python**

Call `merge_positions()` on an `AsyncSecureClient`. The synchronous
`SecureClient` provides the same method. Both clients identify the
position system, select the correct route, and check the wallet's
mergeable balance.

```python theme={null}
transaction = await client.merge_positions(
    condition_id=market.condition_id,
    amount="max",
)

outcome = await transaction.wait()

# outcome.transaction_hash: TransactionHash
# outcome.transaction_id: str | None
```

Pass a base-unit `int` to merge a specific amount. `"max"` merges the
smaller of the wallet's YES and NO balances. `wait()` returns a
`TransactionOutcome` after the transaction settles.

**API**

Encode the merge call, then sign and submit it as a Deposit Wallet batch.

**1. Build the Position Call**

Use the target from the API table in the setup above and the arguments for your position system:

| Argument | Polymarket V2 | CTF |
| - | - | - |
| `conditionId` | 31-byte Polymarket V2 condition ID | Original 32-byte CTF condition ID |
| `collateralToken` | — | pUSD: `0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB` |
| `parentCollectionId` | — | 32 zero bytes |
| `partition` | — | `[1, 2]` |
| `amount` | Amount in six-decimal base units | Amount in six-decimal base units |

ABI-encode the function for your market's protocol using the arguments above:

```solidity Polymarket V2 theme={null}
function merge(bytes31 conditionId, uint256 amount);
```

```solidity CTF theme={null}
function mergePositions(
    address collateralToken,
    bytes32 parentCollectionId,
    bytes32 conditionId,
    uint256[] partition,
    uint256 amount
);
```

Place the resulting calldata in the wallet call list:

```json theme={null}
[
  {
    "target": "<position_target_address>",
    "value": "0",
    "data": "<operation_calldata>"
  }
]
```

Use this list as `calls` in the wallet batch.

**2. Submit the Batch**

Include the encoded call in a signed Deposit Wallet batch and submit it with the Relayer API key:

```bash theme={null}
curl -X POST "https://relayer-v2.polymarket.com/submit" \
  -H "Content-Type: application/json" \
  -H "RELAYER_API_KEY: $RELAYER_API_KEY" \
  -H "RELAYER_API_KEY_ADDRESS: $RELAYER_API_KEY_ADDRESS" \
  --data '{
  "type": "WALLET",
  "from": "<signer_address>",
  "to": "0x00000000000Fb5C9ADea0298D729A0CB3823Cc07",
  "nonce": "<wallet_nonce>",
  "signature": "<wallet_batch_signature>",
  "metadata": "Merge position",
  "depositWalletParams": {
    "depositWallet": "<deposit_wallet_address>",
    "deadline": "<unix_seconds>",
    "calls": [
      {
        "target": "<position_target_address>",
        "value": "0",
        "data": "<operation_calldata>"
      }
    ]
  }
}'
```

See [Execute Gasless Transactions](/trading/wallets-auth.md#execute-gasless-transactions) for nonce creation, wallet-batch signing, and confirmation. Wait for `STATE_CONFIRMED` before using the new balances.

**Solidity**

Approve the selected position contract, then execute the merge from the same address.

**1. Approve Outcome Transfers**

Call `setApprovalForAll` on the outcome-token ledger from the address holding the positions:

```solidity theme={null}
function setApprovalForAll(address operator, bool approved) external;
```

| Position system | Token ledger | Operator | Approved |
| - | - | - | - |
| Polymarket V2 | PositionManager | Router | `true` |
| CTF | Conditional Tokens | Selected collateral adapter | `true` |

Use the addresses in the Solidity setup above. An existing operator approval is sufficient.

**2. Merge the Positions**

Use the approved target from the Solidity table in the setup above. Call the function for your position system:

```solidity Polymarket V2 theme={null}
function merge(bytes31 conditionId, uint256 amount);
```

```solidity CTF theme={null}
function mergePositions(
    address collateralToken,
    bytes32 parentCollectionId,
    bytes32 conditionId,
    uint256[] partition,
    uint256 amount
);
```

| Argument | Polymarket V2 | CTF |
| - | - | - |
| `conditionId` | 31-byte Polymarket V2 condition ID | Original 32-byte CTF condition ID |
| `collateralToken` | — | pUSD: `0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB` |
| `parentCollectionId` | — | 32 zero bytes |
| `partition` | — | `[1, 2]` |
| `amount` | Amount in six-decimal base units | Amount in six-decimal base units |

## Redeem Resolved Positions

Redeeming converts outcome tokens into pUSD after a market resolves. Each
winning token returns 1 pUSD, while losing tokens return 0.

```text theme={null}
Market resolves YES:
100 YES tokens → 100 pUSD
100 NO tokens  → 0 pUSD
```

> [!NOTE]
>
> There is no redemption deadline. Winning tokens remain redeemable at any time
> after resolution.

Before redeeming, ensure you have:

1. A resolved market.
2. Outcome tokens for the market.
3. Approved the position call target for the market's position system to
   transfer the wallet's outcome tokens.

**TypeScript**

Call `redeemPositions()` on a `SecureClient`. The client uses the condition
ID to select the correct route for the market's position system.

```ts theme={null}
const transaction = await client.redeemPositions({
  conditionId: market.conditionId,
});

const outcome = await transaction.wait();

// outcome.transactionHash: TxHash
// outcome.transactionId: TransactionId | null
```

Redemption has no amount parameter: it redeems the wallet's balances for
both outcomes. `wait()` returns a `TransactionOutcome` after the transaction
settles.

**Python**

Call `redeem_positions()` on an `AsyncSecureClient`. The synchronous
`SecureClient` provides the same method. Both clients use the condition ID
to select the correct route for the market's position system.

```python theme={null}
transaction = await client.redeem_positions(
    condition_id=market.condition_id,
)

outcome = await transaction.wait()

# outcome.transaction_hash: TransactionHash
# outcome.transaction_id: str | None
```

Redemption has no amount parameter: it redeems the wallet's balances for
both outcomes. `wait()` returns a `TransactionOutcome` after the transaction
settles.

**API**

Encode the redeem call, then sign and submit it as a Deposit Wallet batch.

**1. Build the Position Call**

Use the target from the API table in the setup above and the arguments for your position system:

| Argument | Polymarket V2 | CTF |
| - | - | - |
| `conditionId` | 31-byte Polymarket V2 condition ID | Original 32-byte CTF condition ID |
| `collateralToken` | — | pUSD: `0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB` |
| `parentCollectionId` | — | 32 zero bytes |
| `indexSets` | — | `[1, 2]` to redeem both outcomes |
| `outcomeIndex` | `0` for YES or `1` for NO | — |
| `amount` | Amount to redeem for the selected outcome, in base units | Redeems the selected outcome balances in full |

ABI-encode the function for your market's protocol using the arguments above:

```solidity Polymarket V2 theme={null}
function redeem(bytes31 conditionId, uint256 outcomeIndex, uint256 amount);
```

```solidity CTF theme={null}
function redeemPositions(
    address collateralToken,
    bytes32 parentCollectionId,
    bytes32 conditionId,
    uint256[] indexSets
);
```

Build the call list for the outcomes you want to redeem. Polymarket Protocol V2 uses one call per outcome. CTF can redeem both in one call. Include the calls in the same wallet batch.

```json Polymarket V2 theme={null}
[
  {
    "target": "<position_target_address>",
    "value": "0",
    "data": "<yes_operation_calldata>"
  },
  {
    "target": "<position_target_address>",
    "value": "0",
    "data": "<no_operation_calldata>"
  }
]
```

```json CTF theme={null}
[
  {
    "target": "<position_target_address>",
    "value": "0",
    "data": "<operation_calldata>"
  }
]
```

Use this list as `calls` in the wallet batch.

> **Derive V2 Inputs From a Position ID**
>
> If you start with a Polymarket Protocol V2 Position ID, derive the condition and outcome locally, retaining all 256 bits:
>
> ```text Pseudocode theme={null}
> positionId = parseUnsigned256("<position_id>")
> conditionId = encodeBytes31(positionId >> 8)
> outcomeIndex = positionId & 255
> ```
>
> `encodeBytes31` writes exactly 31 big-endian bytes, preserving leading zeros. For a YES/NO position, the outcome index is `0` or `1`.

**2. Submit the Batch**

Include the selected call list in the signed Deposit Wallet batch and submit it with the Relayer API key. This example redeems both Polymarket Protocol V2 outcomes:

```bash theme={null}
curl -X POST "https://relayer-v2.polymarket.com/submit" \
  -H "Content-Type: application/json" \
  -H "RELAYER_API_KEY: $RELAYER_API_KEY" \
  -H "RELAYER_API_KEY_ADDRESS: $RELAYER_API_KEY_ADDRESS" \
  --data '{
  "type": "WALLET",
  "from": "<signer_address>",
  "to": "0x00000000000Fb5C9ADea0298D729A0CB3823Cc07",
  "nonce": "<wallet_nonce>",
  "signature": "<wallet_batch_signature>",
  "metadata": "Redeem position",
  "depositWalletParams": {
    "depositWallet": "<deposit_wallet_address>",
    "deadline": "<unix_seconds>",
    "calls": [
      {
        "target": "<position_target_address>",
        "value": "0",
        "data": "<yes_operation_calldata>"
      },
      {
        "target": "<position_target_address>",
        "value": "0",
        "data": "<no_operation_calldata>"
      }
    ]
  }
}'
```

See [Execute Gasless Transactions](/trading/wallets-auth.md#execute-gasless-transactions) for nonce creation, wallet-batch signing, and confirmation. Wait for `STATE_CONFIRMED` before using the new balances.

**Solidity**

Approve the selected position contract, then execute the redeem from the same address.

**1. Approve Outcome Transfers**

Call `setApprovalForAll` on the outcome-token ledger from the address holding the positions:

```solidity theme={null}
function setApprovalForAll(address operator, bool approved) external;
```

| Position system | Token ledger | Operator | Approved |
| - | - | - | - |
| Polymarket V2 | PositionManager | Router | `true` |
| CTF | Conditional Tokens | Selected collateral adapter | `true` |

Use the addresses in the Solidity setup above. An existing operator approval is sufficient.

**2. Redeem the Positions**

Use the approved target from the Solidity table in the setup above. Call the function for your position system:

```solidity Polymarket V2 theme={null}
function redeem(bytes31 conditionId, uint256 outcomeIndex, uint256 amount);
```

```solidity CTF theme={null}
function redeemPositions(
    address collateralToken,
    bytes32 parentCollectionId,
    bytes32 conditionId,
    uint256[] indexSets
);
```

| Argument | Polymarket V2 | CTF |
| - | - | - |
| `conditionId` | 31-byte Polymarket V2 condition ID | Original 32-byte CTF condition ID |
| `collateralToken` | — | pUSD: `0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB` |
| `parentCollectionId` | — | 32 zero bytes |
| `indexSets` | — | `[1, 2]` to redeem both outcomes |
| `outcomeIndex` | `0` for YES or `1` for NO | — |
| `amount` | Amount to redeem for the selected outcome, in base units | Redeems the selected outcome balances in full |

Polymarket Protocol V2 redeems one outcome and an explicit amount per call. Both calls can execute in one transaction. CTF redeems both selected outcome balances in one call.

> **Derive V2 Inputs From a Position ID**
>
> If you start with a Polymarket Protocol V2 Position ID, derive the condition and outcome locally, retaining all 256 bits:
>
> ```text Pseudocode theme={null}
> positionId = parseUnsigned256("<position_id>")
> conditionId = encodeBytes31(positionId >> 8)
> outcomeIndex = positionId & 255
> ```
>
> `encodeBytes31` writes exactly 31 big-endian bytes, preserving leading zeros. For a YES/NO position, the outcome index is `0` or `1`.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.