> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Builder Codes

> Earn builder fees for routing orders and building new Polymarket Perps experiences

Builder Attribution lets applications earn an additional fee for routing Perps
orders with a fee explicitly approved by the trader.

The **Builder** operates the application and receives the fee in its Perps
account. The **Trader** places orders through that application and approves the
Builder fee.

## Quickstart: Place an order with Builder Attribution

Use the Trader's [funded Perps account](/perps/fund-your-account.md) and owner signer
to approve the Builder fee once, then place attributed orders. Approval remains
valid across sessions until revoked or replaced.

Each Builder is identified by its Perps account address. The Builder and Trader
must be different accounts.

**TypeScript**

Use `createSecureClient()` from `@polymarket/client` with the Trader's owner
signer. This example uses Viem. See
[Wallet Integrations](/getting-started/typescript.md#wallet-integrations) for other
signer providers. Set `builderAddress` to the Builder's EVM account address and
`instrumentId` to the instrument selected in [Prepare a Trade](/perps/trading.md#prepare-a-trade).

**1. Create a Secure Client**

Create a `SecureClient` for the Trader. Set `POLYMARKET_WALLET_ADDRESS` to the
Polymarket wallet that owns the Perps account and `PRIVATE_KEY` to its owner's
signing key.

```ts theme={null}
import { createSecureClient } from "@polymarket/client";
import { privateKey } from "@polymarket/client/viem";

const client = await createSecureClient({
  wallet: process.env.POLYMARKET_WALLET_ADDRESS!,
  signer: privateKey(process.env.PRIVATE_KEY!),
});
```

**2. Open a Trader Session**

Call `client.openPerpsSession()` with the Builder's account address:

```ts theme={null}
const session = await client.openPerpsSession({
  builderAttribution: builderAddress,
});
```

**3. Approve the Builder Fee**

Call `session.approveBuilderFee` with an explicit `maxFeeRate` before the Trader's
first attributed order. Skip this step if the Trader already has an active approval:

```ts theme={null}
await session.approveBuilderFee({ maxFeeRate: "0.001" });
```

> **Output: PerpsBuilderApproval**
>
> ```ts PerpsBuilderApproval Type theme={null}
> type PerpsBuilderApproval = {
>   trader: EvmAddress;
>   builder: EvmAddress;
>   maxFeeRate: DecimalString; // Zero revokes consent
>   approvalVersion: number;
>   timestamp: EpochMilliseconds;
>   sequence: number;
> };
> ```
>
> ```json PerpsBuilderApproval Example theme={null}
> {
>   "trader": "0x0000000000000000000000000000000000005678",
>   "builder": "0x0000000000000000000000000000000000001234",
>   "maxFeeRate": "0.001",
>   "approvalVersion": 1,
>   "timestamp": 1767000000000,
>   "sequence": 1234567889
> }
> ```

**4. Place an Attributed Order**

Call `session.placeOrder()` to submit an immediate-or-cancel buy using the
session's Builder:

```ts theme={null}
import { OrderSide, PerpsTimeInForce } from "@polymarket/client";

const result = await session.placeOrder({
  instrumentId,
  side: OrderSide.BUY,
  quantity: "0.01",
  timeInForce: PerpsTimeInForce.IOC,
});
```

> **Output: PlacePerpsOrderResult**
>
> ```ts PlacePerpsOrderResult Type theme={null}
> type PlacePerpsOrderResult = {
>   order: PerpsOrder;
> };
>
> type PerpsOrder = {
>   id: PerpsOrderId;
>   instrumentId: PerpsInstrumentId;
>   side: OrderSide;
>   price: DecimalString;
>   quantity: DecimalString;
>   timeInForce: PerpsTimeInForce;
>   postOnly: boolean;
>   reduceOnly: boolean;
>   status: PerpsOrderStatus;
>   restingQuantity: DecimalString;
>   filledQuantity: DecimalString;
>   createdTimestamp: EpochMilliseconds;
>   updatedTimestamp: EpochMilliseconds;
>   clientOrderId: string | undefined;
>   tpSl: PerpsTpSlOrderFields | undefined;
>   builder?: PerpsBuilderTerms;
> };
>
> type PerpsBuilderTerms = {
>   address: EvmAddress;
>   feeRate: DecimalString;
> };
> ```
>
> ```json PlacePerpsOrderResult Example theme={null}
> {
>   "order": {
>     "id": 67890,
>     "instrumentId": 1,
>     "side": "BUY",
>     "price": "65000",
>     "quantity": "0.01",
>     "timeInForce": "ioc",
>     "postOnly": false,
>     "reduceOnly": false,
>     "status": "filled",
>     "restingQuantity": "0",
>     "filledQuantity": "0.01",
>     "createdTimestamp": 1767000010000,
>     "updatedTimestamp": 1767000010000,
>     "builder": {
>       "address": "0x0000000000000000000000000000000000001234",
>       "feeRate": "0.001"
>     }
>   }
> }
> ```

The session applies the same terms to single orders, batches, and generated
take-profit and stop-loss exits. Await approval before submitting orders that
should use its terms.

**Python**

Use `AsyncSecureClient` from `polymarket-client` and run the examples inside an async
function. `builder_address` is your enabled Builder's account address.
`instrument_id` identifies the instrument selected in
[Prepare a Trade](/perps/trading.md#prepare-a-trade).

**1. Create a Secure Client**

Create an `AsyncSecureClient` for the Trader. Set `POLYMARKET_WALLET_ADDRESS`
to the Polymarket wallet that owns the Perps account and `PRIVATE_KEY` to its
owner's signing key.

```python theme={null}
import os

from polymarket import AsyncSecureClient

client = await AsyncSecureClient.create(
    private_key=os.environ["PRIVATE_KEY"],
    wallet=os.environ["POLYMARKET_WALLET_ADDRESS"],
)
```

**2. Open a Trader Session**

Call `client.open_perps_session()` with the Builder's account address:

```python theme={null}
session = await client.open_perps_session(builder_attribution=builder_address)
```

**3. Approve the Builder Fee**

Call `session.approve_builder_fee` with an explicit `max_fee_rate` before the Trader's
first attributed order. Skip this step if the Trader already has an active approval:

```python theme={null}
await session.approve_builder_fee(max_fee_rate="0.001")
```

> **Output: PerpsBuilderApproval**
>
> ```python PerpsBuilderApproval Type theme={null}
> class PerpsBuilderApproval:
>     trader: EvmAddress
>     builder: EvmAddress
>     max_fee_rate: Decimal  # Zero revokes consent
>     approval_version: int
>     timestamp: datetime
>     sequence: int
> ```
>
> ```json PerpsBuilderApproval Example theme={null}
> {
>   "trader": "0x0000000000000000000000000000000000005678",
>   "builder": "0x0000000000000000000000000000000000001234",
>   "max_fee_rate": "0.001",
>   "approval_version": 1,
>   "timestamp": "2025-12-29T09:20:00Z",
>   "sequence": 1234567889
> }
> ```

**4. Place an Attributed Order**

Call `session.place_order()` to submit an immediate-or-cancel buy using the
session's Builder:

```python theme={null}
result = await session.place_order(
    instrument_id=instrument_id,
    side="BUY",
    quantity="0.01",
    time_in_force="ioc",
)
```

> **Output: PerpsOrderPlacement**
>
> ```python PerpsOrderPlacement Type theme={null}
> class PerpsOrderPlacement:
>     order: PerpsOrder
>     tp_sl: PerpsPlacedTpSlOrders | None = None
>
>
> class PerpsOrder:
>     id: PerpsOrderId
>     instrument_id: PerpsInstrumentId
>     side: OrderSide
>     price: Decimal
>     quantity: Decimal
>     time_in_force: PerpsTimeInForce
>     post_only: bool
>     reduce_only: bool
>     status: PerpsOrderStatus
>     resting_quantity: Decimal
>     filled_quantity: Decimal
>     created_at: datetime
>     updated_at: datetime
>     client_order_id: str | None = None
>     tp_sl: PerpsTpSlOrderFields | None = None
>     builder: PerpsBuilderAttribution | None = None
>
>
> class PerpsBuilderAttribution:
>     address: EvmAddress
>     fee_rate: Decimal
> ```
>
> ```json PerpsOrderPlacement Example theme={null}
> {
>   "order": {
>     "id": 67890,
>     "instrument_id": 1,
>     "side": "BUY",
>     "price": "65000",
>     "quantity": "0.01",
>     "time_in_force": "ioc",
>     "post_only": false,
>     "reduce_only": false,
>     "status": "filled",
>     "resting_quantity": "0",
>     "filled_quantity": "0.01",
>     "builder": {
>       "address": "0x0000000000000000000000000000000000001234",
>       "fee_rate": "0.001"
>     },
>     "created_at": "2025-12-29T09:20:10Z",
>     "updated_at": "2025-12-29T09:20:10Z"
>   }
> }
> ```

The session applies the same terms to single orders, batches, and generated
take-profit and stop-loss exits. Await approval before submitting orders that
should use its terms.

**API**

Use the account owner's signer for fee approval and a trading proxy for orders.
Set up proxy credentials through [Authenticated
Sessions](/perps/authenticated-sessions.md#set-up-perps-access) first. The examples
below use the same signing flow as [Place Orders](/perps/trading.md#place-orders).

**1. Read the Saved Approval**

Fetch the Trader's grant using its proxy credentials:

```bash Request theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/builder-approvals" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "builder=<builder_account_address>"
```

```ts BuilderApprovals Type theme={null}
type BuilderApprovals = {
  data: Array<{
    trader: string;
    builder: string;
    max_fee_rate: string;
    approval_version: number;
    timestamp: number;
    sequence: number;
  }>;
};
```

```json BuilderApprovals Example theme={null}
{
  "data": []
}
```

The response's `data` array includes revoked grants. Use the saved
`approval_version` plus one, or `1` if no grant exists.

**2. Build the Approval Operation**

Read the Builder's configured fee for this approval:

```ts theme={null}
const builderAddress = "0x0000000000000000000000000000000000001234";
const builderStatusResponse = await fetch(
  `https://api.perpetuals.polymarket.com/v1/info/builder?address=${builderAddress}`,
);
const builderStatus = await builderStatusResponse.json();
const builderFeeRate = builderStatus.max_fee_rate;
```

> **Output: BuilderStatus**
>
> ```ts BuilderStatus Type theme={null}
> type BuilderStatus = {
>   address: string;
>   registered: boolean;
>   enabled: boolean;
>   admission_enabled: boolean;
>   max_fee_rate: string;
> };
> ```
>
> ```json BuilderStatus Example theme={null}
> {
>   "address": "0x0000000000000000000000000000000000001234",
>   "registered": true,
>   "enabled": true,
>   "admission_enabled": true,
>   "max_fee_rate": "0.001"
> }
> ```

Use `builderFeeRate` as `<builder_fee_rate>` below and the saved approval
version plus one as `approval_version`. This example uses `1` for the first
grant. The account owner signs these terms:

```json theme={null}
{
  "type": "approveBuilder",
  "args": {
    "builder": "0x0000000000000000000000000000000000001234",
    "max_fee_rate": "<builder_fee_rate>",
    "approval_version": 1
  }
}
```

**3. Create the Approval Hash**

MessagePack-encode the compact approval operation, then hash the bytes with
`keccak256`. This example uses `@msgpack/msgpack` and Viem:

```ts theme={null}
import { encode } from "@msgpack/msgpack";
import { keccak256 } from "viem";

const approvalOperation = [
  "approveBuilder",
  [builderAddress, builderFeeRate, 1],
];
const approvalHash = keccak256(encode(approvalOperation));
```

The address, rate, and version must match the structured operation above.

**4. Create Approval Typed Data**

Create an EIP-712 `Op` payload using the operation hash:

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
      {
        "name": "data",
        "type": "bytes32"
      },
      {
        "name": "salt",
        "type": "uint64"
      },
      {
        "name": "ts",
        "type": "uint64"
      }
    ]
  },
  "message": {
    "data": "<approval_hash>",
    "salt": 123456789,
    "ts": 1767000000000
  }
}
```

Replace the sample `salt` with a fresh random integer and `ts` with the current
Unix timestamp in milliseconds. Use those same values when signing and submitting
the request. The production signing domain uses `chainId: 137`.

**5. Sign Approval Typed Data**

Sign with the account owner's key using Viem. Set `OWNER_PRIVATE_KEY` to that
key before running the example:

```ts Viem theme={null}
import { privateKeyToAccount } from "viem/accounts";

const account = privateKeyToAccount(
  process.env.OWNER_PRIVATE_KEY as `0x${string}`,
);
const approvalSignature = await account.signTypedData({
  domain: { name: "Polymarket", version: "1", chainId: 137 },
  primaryType: "Op",
  types: {
    Op: [
      { name: "data", type: "bytes32" },
      { name: "salt", type: "uint64" },
      { name: "ts", type: "uint64" },
    ],
  },
  message: {
    data: approvalHash,
    salt: 123456789n,
    ts: 1767000000000n,
  },
});
```

**6. Submit the Approval**

Submit the operation with `approvalSignature` as `<approval_signature>` and the
same salt and timestamp used in the signed typed data:

```bash Request theme={null}
curl -X POST "https://api.perpetuals.polymarket.com/v1/account/builder-approvals" \
  -H "Content-Type: application/json" \
  -d '{
    "op": {
      "type": "approveBuilder",
      "args": {
        "builder": "0x0000000000000000000000000000000000001234",
        "max_fee_rate": "<builder_fee_rate>",
        "approval_version": 1
      }
    },
    "sig": "<approval_signature>",
    "salt": 123456789,
    "ts": 1767000000000
  }'
```

```ts BuilderApproval Type theme={null}
type BuilderApproval = {
  trader: string;
  builder: string;
  max_fee_rate: string;
  approval_version: number;
  timestamp: number;
  sequence: number;
};
```

```json BuilderApproval Example theme={null}
{
  "trader": "0x0000000000000000000000000000000000005678",
  "builder": "0x0000000000000000000000000000000000001234",
  "max_fee_rate": "0.001",
  "approval_version": 1,
  "timestamp": 1767000000000,
  "sequence": 1234567889
}
```

The response contains the committed `approval_version`, `max_fee_rate`, and
`sequence`. Check that its Builder, rate, and version match the request before
placing an order. If the result is unclear, read the saved grant before signing
a replacement.

**7. Build the Order Operation**

Choose an instrument, price, and quantity as shown in
[Prepare a Trade](/perps/trading.md#prepare-a-trade). This example places an
immediate-or-cancel buy with a limit price of `65000` and the Builder fee
approved above:

```json theme={null}
{
  "type": "createOrders",
  "args": [
    {
      "iid": 1,
      "buy": true,
      "p": "65000",
      "qty": "0.01",
      "tif": "ioc",
      "po": false,
      "c": "7f9e4a2b6c8d0e1f1234567890abcdef",
      "builder": {
        "address": "0x0000000000000000000000000000000000001234",
        "fee_rate": "<builder_fee_rate>"
      }
    }
  ]
}
```

**8. Create the Order Hash**

Append the Builder tuple after the existing order fields, then omit undefined
optional entries before MessagePack encoding. Use the same values as the order
operation above:

```ts theme={null}
import { encode } from "@msgpack/msgpack";
import { keccak256 } from "viem";

const signableOrder = [
  "createOrders",
  [
    [
      1,
      true,
      "65000",
      "0.01",
      "ioc",
      false,
      undefined, // ro
      "7f9e4a2b6c8d0e1f1234567890abcdef",
      undefined, // tr
      undefined, // gtt
      [builderAddress, builderFeeRate],
    ].filter((value) => value !== undefined),
  ],
];
const orderHash = keccak256(encode(signableOrder));
```

**9. Create Order Typed Data**

Create an EIP-712 `Op` payload using the operation hash:

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
      {
        "name": "data",
        "type": "bytes32"
      },
      {
        "name": "salt",
        "type": "uint64"
      },
      {
        "name": "ts",
        "type": "uint64"
      }
    ]
  },
  "message": {
    "data": "<order_hash>",
    "salt": 234567890,
    "ts": 1767000010000
  }
}
```

Replace the sample `salt` with a fresh random integer and `ts` with the current
Unix timestamp in milliseconds. Use those same values when signing and submitting
the request. The production signing domain uses `chainId: 137`.

**10. Sign Order Typed Data**

Sign with the trading proxy's key using Viem. Set `PROXY_PRIVATE_KEY` to that
key before running the example:

```ts Viem theme={null}
import { privateKeyToAccount } from "viem/accounts";

const account = privateKeyToAccount(
  process.env.PROXY_PRIVATE_KEY as `0x${string}`,
);
const orderSignature = await account.signTypedData({
  domain: { name: "Polymarket", version: "1", chainId: 137 },
  primaryType: "Op",
  types: {
    Op: [
      { name: "data", type: "bytes32" },
      { name: "salt", type: "uint64" },
      { name: "ts", type: "uint64" },
    ],
  },
  message: {
    data: orderHash,
    salt: 234567890n,
    ts: 1767000010000n,
  },
});
```

**11. Submit the Order**

Submit `orderSignature` as `<order_signature>`, keeping the operation, salt, and
timestamp identical to the signed values:

```bash Request theme={null}
curl -X POST "https://api.perpetuals.polymarket.com/v1/trade/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "op": {
      "type": "createOrders",
      "args": [
        {
          "iid": 1,
          "buy": true,
          "p": "65000",
          "qty": "0.01",
          "tif": "ioc",
          "po": false,
          "c": "7f9e4a2b6c8d0e1f1234567890abcdef",
          "builder": {
            "address": "0x0000000000000000000000000000000000001234",
            "fee_rate": "<builder_fee_rate>"
          }
        }
      ]
    },
    "sig": "<order_signature>",
    "salt": 234567890,
    "ts": 1767000010000
  }'
```

```ts OrderResponse Type theme={null}
type OrderResponse = {
  status: "ok";
  oid: number;
  coid?: string;
  builder?: {
    address: string;
    fee_rate: string;
  };
} | {
  status: "err";
  error: string;
  oid?: number;
  coid?: string;
};
```

```json OrderResponse Example theme={null}
[
  {
    "status": "ok",
    "oid": 67890,
    "coid": "7f9e4a2b6c8d0e1f1234567890abcdef",
    "builder": {
      "address": "0x0000000000000000000000000000000000001234",
      "fee_rate": "0.001"
    }
  }
]
```

Check each result in the response array. `status: "ok"` confirms acceptance and
`oid` identifies the order. `status: "err"` includes the rejection in `error`.
Acceptance does not confirm execution.

**12. Check the Order State**

Use the returned `oid` to read the order with the Trader's proxy credentials:

```bash Request theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/orders" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "order_id=<oid>"
```

```ts OrderData Type theme={null}
type OrderData = {
  order_id: number;
  instrument_id: number;
  buy: boolean;
  price: string;
  quantity: string;
  tif: "gtc" | "ioc" | "fok";
  post_only: boolean;
  ro: boolean;
  resting_quantity: string;
  filled_quantity: string;
  status: string;
  created_timestamp: number;
  updated_timestamp: number;
  client_order_id?: string;
  tpsl?: {
    kind: "tp" | "sl";
    scope: "order" | "position";
    trp: string;
    parent_oid?: number;
    armed_qty?: string;
    slip_bps?: number;
  };
  builder?: {
    address: string;
    fee_rate: string;
  };
};
```

```json OrderData Example theme={null}
[
  {
    "order_id": 67890,
    "instrument_id": 1,
    "buy": true,
    "price": "65000",
    "quantity": "0.01",
    "tif": "ioc",
    "post_only": false,
    "ro": false,
    "status": "filled",
    "resting_quantity": "0",
    "filled_quantity": "0.01",
    "created_timestamp": 1767000010000,
    "updated_timestamp": 1767000010000,
    "client_order_id": "7f9e4a2b6c8d0e1f1234567890abcdef",
    "builder": {
      "address": "0x0000000000000000000000000000000000001234",
      "fee_rate": "0.001"
    }
  }
]
```

Read `status`, `filled_quantity`, and `resting_quantity` to determine the
execution outcome. Full order reads include the saved `builder` terms.
If the snapshot is not available yet, retry the read or follow private order
updates through [Authenticated Sessions](/perps/authenticated-sessions.md#session-lifecycle).

## Trader Operations

Read and manage the Trader's consent, place attributed orders, and inspect the
fees charged on executions.

### Fetch Trader Approval

Check whether the Trader has already approved a Builder and the maximum fee
they accepted. Read the saved approval before asking for consent again. A
revoked approval remains in the account's history with a zero fee limit.

**TypeScript**

Call `session.fetchBuilderApprovals()` on the Trader's existing `PerpsSession`,
filtering by the Builder's account address:

```ts theme={null}
const approvals = await session.fetchBuilderApprovals({
  builder: builderAddress,
});
const approval = approvals[0];
```

> **Output: PerpsBuilderApproval\[]**
>
> ```ts PerpsBuilderApproval Type theme={null}
> type PerpsBuilderApproval = {
>   trader: EvmAddress;
>   builder: EvmAddress;
>   maxFeeRate: DecimalString; // Zero revokes consent
>   approvalVersion: number;
>   timestamp: EpochMilliseconds;
>   sequence: number;
> };
> ```
>
> ```json PerpsBuilderApproval Example theme={null}
> [
>   {
>     "trader": "0x0000000000000000000000000000000000005678",
>     "builder": "0x0000000000000000000000000000000000001234",
>     "maxFeeRate": "0.001",
>     "approvalVersion": 1,
>     "timestamp": 1767000000000,
>     "sequence": 1234567889
>   }
> ]
> ```

An empty array means the Trader has no saved approval for this Builder.
Otherwise, `approval.maxFeeRate` is the approved fee limit as a decimal string.
Zero means consent was revoked. Compare the intended Builder fee with this
limit to decide whether to request approval. `approval.approvalVersion` is the
saved version, including after revocation.

Omit the `builder` filter to fetch all of the Trader's saved Builder approvals.

**Python**

Call `session.fetch_builder_approvals()` on the Trader's existing `PerpsSession`,
filtering by the Builder's account address:

```python theme={null}
approvals = await session.fetch_builder_approvals(builder=builder_address)
approval = approvals[0] if approvals else None
```

> **Output: tuple\[PerpsBuilderApproval, …]**
>
> ```python PerpsBuilderApproval Type theme={null}
> class PerpsBuilderApproval:
>     trader: EvmAddress
>     builder: EvmAddress
>     max_fee_rate: Decimal  # Zero revokes consent
>     approval_version: int
>     timestamp: datetime
>     sequence: int
> ```
>
> ```json PerpsBuilderApproval Example theme={null}
> [
>   {
>     "trader": "0x0000000000000000000000000000000000005678",
>     "builder": "0x0000000000000000000000000000000000001234",
>     "max_fee_rate": "0.001",
>     "approval_version": 1,
>     "timestamp": "2025-12-29T09:20:00Z",
>     "sequence": 1234567889
>   }
> ]
> ```

An empty tuple means the Trader has no saved approval for this Builder.
Otherwise, `approval.max_fee_rate` is the approved fee limit as a `Decimal`.
Zero means consent was revoked. Compare the intended Builder fee with this
limit to decide whether to request approval. `approval.approval_version` is the
saved version, including after revocation.

Omit the `builder` filter to fetch all of the Trader's saved Builder approvals.

**API**

Fetch the Trader's saved approval for the Builder using the Trader's proxy
credentials:

```bash Request theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/builder-approvals" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "builder=<builder_account_address>"
```

```ts BuilderApprovals Type theme={null}
type BuilderApprovals = {
  data: Array<{
    trader: string;
    builder: string;
    max_fee_rate: string;
    approval_version: number;
    timestamp: number;
    sequence: number;
  }>;
};
```

```json BuilderApprovals Example theme={null}
{
  "data": [
    {
      "trader": "0x0000000000000000000000000000000000005678",
      "builder": "0x0000000000000000000000000000000000001234",
      "max_fee_rate": "0.001",
      "approval_version": 1,
      "timestamp": 1767000000000,
      "sequence": 1234567889
    }
  ]
}
```

An empty `data` array means the Trader has no saved approval for this Builder.
Otherwise, `max_fee_rate` is the approved fee limit. `"0"` means consent was
revoked. Compare the intended `builder.fee_rate` with this limit to decide
whether to request approval. `approval_version` is the saved version, including
after revocation. Omit the `builder` query parameter to fetch all saved Builder
approvals for the authenticated Trader.

Check whether the Builder can currently receive attributed orders:

```bash Request theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/info/builder" \
  --data-urlencode "address=<builder_account_address>"
```

```ts BuilderStatus Type theme={null}
type BuilderStatus = {
  address: string;
  registered: boolean;
  enabled: boolean;
  admission_enabled: boolean;
  max_fee_rate: string;
};
```

```json BuilderStatus Example theme={null}
{
  "address": "0x0000000000000000000000000000000000001234",
  "registered": true,
  "enabled": true,
  "admission_enabled": true,
  "max_fee_rate": "0.001"
}
```

Require `registered`, `enabled`, and `admission_enabled` to be `true`. The order's
`builder.fee_rate` must be covered by both the Builder's current configuration
and the Trader's positive approval. Fetching these values does not create or
change consent. The server validates them again when it receives an order.

### Grant Approval

Approve the Builder fee once before placing attributed orders. Existing approval
remains valid across sessions until revoked or replaced. To approve a changed
Builder fee, grant approval again.

> [!WARNING]
>
> The maximum Builder fee is currently 10 basis points (0.1%). This limit may
> change in the future.

**TypeScript**

Call `session.approveBuilderFee` on a Trader session opened with
`builderAttribution`. Pass the maximum fee the Trader agrees to authorize:

```ts theme={null}
await session.approveBuilderFee({ maxFeeRate: "0.001" });
```

`maxFeeRate` is required and has no default. This example approves 10 basis points
(0.1%). To select a Builder when the session has none, or approve a different
Builder, also pass its address:

```ts theme={null}
await session.approveBuilderFee({
  builderAddress,
  maxFeeRate: "0.001",
});
```

> **Output: PerpsBuilderApproval**
>
> ```ts PerpsBuilderApproval Type theme={null}
> type PerpsBuilderApproval = {
>   trader: EvmAddress;
>   builder: EvmAddress;
>   maxFeeRate: DecimalString; // Zero revokes consent
>   approvalVersion: number;
>   timestamp: EpochMilliseconds;
>   sequence: number;
> };
> ```
>
> ```json PerpsBuilderApproval Example theme={null}
> {
>   "trader": "0x0000000000000000000000000000000000005678",
>   "builder": "0x0000000000000000000000000000000000001234",
>   "maxFeeRate": "0.001",
>   "approvalVersion": 1,
>   "timestamp": 1767000000000,
>   "sequence": 1234567889
> }
> ```

After confirmation, the session refreshes the Builder cap and uses the lower of
that cap and the approved maximum for new orders.

**Python**

Call `session.approve_builder_fee` on a Trader session opened with
`builder_attribution`. Pass the maximum fee the Trader agrees to authorize:

```python theme={null}
await session.approve_builder_fee(max_fee_rate="0.001")
```

`max_fee_rate` is required and has no default. This example approves 10 basis points
(0.1%). To select a Builder when the session has none, or approve a different
Builder, also pass its address:

```python theme={null}
await session.approve_builder_fee(
    builder_address=builder_address,
    max_fee_rate="0.001",
)
```

> **Output: PerpsBuilderApproval**
>
> ```python PerpsBuilderApproval Type theme={null}
> class PerpsBuilderApproval:
>     trader: EvmAddress
>     builder: EvmAddress
>     max_fee_rate: Decimal  # Zero revokes consent
>     approval_version: int
>     timestamp: datetime
>     sequence: int
> ```
>
> ```json PerpsBuilderApproval Example theme={null}
> {
>   "trader": "0x0000000000000000000000000000000000005678",
>   "builder": "0x0000000000000000000000000000000000001234",
>   "max_fee_rate": "0.001",
>   "approval_version": 1,
>   "timestamp": "2025-12-29T09:20:00Z",
>   "sequence": 1234567889
> }
> ```

After confirmation, the session refreshes the Builder cap and uses the lower of
that cap and the approved maximum for new orders.

**API**

Use the owner-signed `approveBuilder` operation shown in the approval workflow
above. Read the saved grant first and set `approval_version` to its current
version plus one, or `1` for the first grant. Sign and submit the same Builder
address, fee, and version.

### Revoke Approval

Revoke permission for new attributed orders. Existing accepted orders, including pending conditional orders,
retain their saved fees. Cancel those orders to prevent further charges.

**TypeScript**

Call `session.revokeBuilderFee()` with the Builder's account address:

```ts theme={null}
await session.revokeBuilderFee(builderAddress);
```

> **Output: PerpsBuilderApproval**
>
> ```ts PerpsBuilderApproval Type theme={null}
> type PerpsBuilderApproval = {
>   trader: EvmAddress;
>   builder: EvmAddress;
>   maxFeeRate: DecimalString; // Zero revokes consent
>   approvalVersion: number;
>   timestamp: EpochMilliseconds;
>   sequence: number;
> };
> ```
>
> ```json PerpsBuilderApproval Example theme={null}
> {
>   "trader": "0x0000000000000000000000000000000000005678",
>   "builder": "0x0000000000000000000000000000000000001234",
>   "maxFeeRate": "0",
>   "approvalVersion": 2,
>   "timestamp": 1767000030000,
>   "sequence": 1234567891
> }
> ```

**Python**

Call `session.revoke_builder_fee()` with the Builder's account address:

```python theme={null}
await session.revoke_builder_fee(builder_address)
```

> **Output: PerpsBuilderApproval**
>
> ```python PerpsBuilderApproval Type theme={null}
> class PerpsBuilderApproval:
>     trader: EvmAddress
>     builder: EvmAddress
>     max_fee_rate: Decimal  # Zero revokes consent
>     approval_version: int
>     timestamp: datetime
>     sequence: int
> ```
>
> ```json PerpsBuilderApproval Example theme={null}
> {
>   "trader": "0x0000000000000000000000000000000000005678",
>   "builder": "0x0000000000000000000000000000000000001234",
>   "max_fee_rate": "0",
>   "approval_version": 2,
>   "timestamp": "2025-12-29T09:20:30Z",
>   "sequence": 1234567891
> }
> ```

**API**

Use the Trader account owner's signer to replace the saved approval with a
zero maximum rate.

**1. Read the Current Version**

Fetch the latest grant, including its retained version if already revoked:

```bash Request theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/builder-approvals" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>" \
  --data-urlencode "builder=<builder_account_address>"
```

```ts BuilderApprovals Type theme={null}
type BuilderApprovals = {
  data: Array<{
    trader: string;
    builder: string;
    max_fee_rate: string;
    approval_version: number;
    timestamp: number;
    sequence: number;
  }>;
};
```

```json BuilderApprovals Example theme={null}
{
  "data": [
    {
      "trader": "0x0000000000000000000000000000000000005678",
      "builder": "0x0000000000000000000000000000000000001234",
      "max_fee_rate": "0.001",
      "approval_version": 1,
      "timestamp": 1767000000000,
      "sequence": 1234567889
    }
  ]
}
```

Use the returned `approval_version` plus one in the next step. The example uses `2` for a grant whose current version is `1`.

**2. Create the Revocation Hash**

Set the rate to `"0"` and use the next saved version. Hash the compact operation:

```ts theme={null}
import { encode } from "@msgpack/msgpack";
import { keccak256 } from "viem";

const revocationOperation = [
  "approveBuilder",
  ["0x0000000000000000000000000000000000001234", "0", 2],
];
const revocationHash = keccak256(encode(revocationOperation));
```

**3. Create Revocation Typed Data**

Use the same EIP-712 `Op` structure shown in the approval workflow. Set `data`
to `revocationHash` and generate a new random salt and current Unix-millisecond
timestamp. The next examples use sample values that you must replace in both
the signature and the request.

**4. Sign Revocation Typed Data**

Sign with the account owner's key using Viem. Set `OWNER_PRIVATE_KEY` to that
key before running the example:

```ts Viem theme={null}
import { privateKeyToAccount } from "viem/accounts";

const account = privateKeyToAccount(
  process.env.OWNER_PRIVATE_KEY as `0x${string}`,
);
const revocationSignature = await account.signTypedData({
  domain: { name: "Polymarket", version: "1", chainId: 137 },
  primaryType: "Op",
  types: {
    Op: [
      { name: "data", type: "bytes32" },
      { name: "salt", type: "uint64" },
      { name: "ts", type: "uint64" },
    ],
  },
  message: {
    data: revocationHash,
    salt: 456789012n,
    ts: 1767000030000n,
  },
});
```

**5. Submit the Revocation**

Submit `revocationSignature` as `<revocation_signature>` with the matching
operation, salt, and timestamp:

```bash Request theme={null}
curl -X POST "https://api.perpetuals.polymarket.com/v1/account/builder-approvals" \
  -H "Content-Type: application/json" \
  -d '{
    "op": {
      "type": "approveBuilder",
      "args": {
        "builder": "0x0000000000000000000000000000000000001234",
        "max_fee_rate": "0",
        "approval_version": 2
      }
    },
    "sig": "<revocation_signature>",
    "salt": 456789012,
    "ts": 1767000030000
  }'
```

```ts BuilderApproval Type theme={null}
type BuilderApproval = {
  trader: string;
  builder: string;
  max_fee_rate: string;
  approval_version: number;
  timestamp: number;
  sequence: number;
};
```

```json BuilderApproval Example theme={null}
{
  "trader": "0x0000000000000000000000000000000000005678",
  "builder": "0x0000000000000000000000000000000000001234",
  "max_fee_rate": "0",
  "approval_version": 2,
  "timestamp": 1767000030000,
  "sequence": 1234567891
}
```

Existing orders, including pending conditional orders, keep their saved fees.
Cancel them to prevent further charges.

Check that the response has `max_fee_rate: "0"` and the version you submitted.
Stale or replayed versions are rejected. If the result is unclear, read the
saved grant before signing another update.

### Place Orders

The Trader must have granted fee approval for the Builder before submitting
attributed orders. The order's Builder fee must be covered by that approval.
These examples place an immediate-or-cancel buy using an existing trading
session or signed order.

**TypeScript**

Call `session.placeOrder()` on the Trader's existing `PerpsSession`, opened
with `builderAttribution`. The session uses the selected Builder and its saved
approved fee. Set `instrumentId` to the instrument you want to trade:

```ts theme={null}
import { OrderSide, PerpsTimeInForce } from "@polymarket/client";

const result = await session.placeOrder({
  instrumentId,
  side: OrderSide.BUY,
  quantity: "0.01",
  timeInForce: PerpsTimeInForce.IOC,
});
```

> **Output: PlacePerpsOrderResult**
>
> ```ts PlacePerpsOrderResult Type theme={null}
> type PlacePerpsOrderResult = {
>   order: PerpsOrder;
> };
>
> type PerpsOrder = {
>   id: PerpsOrderId;
>   instrumentId: PerpsInstrumentId;
>   side: OrderSide;
>   price: DecimalString;
>   quantity: DecimalString;
>   timeInForce: PerpsTimeInForce;
>   postOnly: boolean;
>   reduceOnly: boolean;
>   status: PerpsOrderStatus;
>   restingQuantity: DecimalString;
>   filledQuantity: DecimalString;
>   createdTimestamp: EpochMilliseconds;
>   updatedTimestamp: EpochMilliseconds;
>   clientOrderId: string | undefined;
>   tpSl: PerpsTpSlOrderFields | undefined;
>   builder?: PerpsBuilderTerms;
> };
>
> type PerpsBuilderTerms = {
>   address: EvmAddress;
>   feeRate: DecimalString;
> };
> ```
>
> ```json PlacePerpsOrderResult Example theme={null}
> {
>   "order": {
>     "id": 67890,
>     "instrumentId": 1,
>     "side": "BUY",
>     "price": "65000",
>     "quantity": "0.01",
>     "timeInForce": "ioc",
>     "postOnly": false,
>     "reduceOnly": false,
>     "status": "filled",
>     "restingQuantity": "0",
>     "filledQuantity": "0.01",
>     "createdTimestamp": 1767000010000,
>     "updatedTimestamp": 1767000010000,
>     "builder": {
>       "address": "0x0000000000000000000000000000000000001234",
>       "feeRate": "0.001"
>     }
>   }
> }
> ```

**Python**

Call `session.place_order()` on the Trader's existing `PerpsSession`, opened
with `builder_attribution`. The session uses the selected Builder and its saved
approved fee. Set `instrument_id` to the instrument you want to trade:

```python theme={null}
result = await session.place_order(
    instrument_id=instrument_id,
    side="BUY",
    quantity="0.01",
    time_in_force="ioc",
)
```

> **Output: PerpsOrderPlacement**
>
> ```python PerpsOrderPlacement Type theme={null}
> class PerpsOrderPlacement:
>     order: PerpsOrder
>     tp_sl: PerpsPlacedTpSlOrders | None = None
>
>
> class PerpsOrder:
>     id: PerpsOrderId
>     instrument_id: PerpsInstrumentId
>     side: OrderSide
>     price: Decimal
>     quantity: Decimal
>     time_in_force: PerpsTimeInForce
>     post_only: bool
>     reduce_only: bool
>     status: PerpsOrderStatus
>     resting_quantity: Decimal
>     filled_quantity: Decimal
>     created_at: datetime
>     updated_at: datetime
>     client_order_id: str | None = None
>     tp_sl: PerpsTpSlOrderFields | None = None
>     builder: PerpsBuilderAttribution | None = None
>
>
> class PerpsBuilderAttribution:
>     address: EvmAddress
>     fee_rate: Decimal
> ```
>
> ```json PerpsOrderPlacement Example theme={null}
> {
>   "order": {
>     "id": 67890,
>     "instrument_id": 1,
>     "side": "BUY",
>     "price": "65000",
>     "quantity": "0.01",
>     "time_in_force": "ioc",
>     "post_only": false,
>     "reduce_only": false,
>     "status": "filled",
>     "resting_quantity": "0",
>     "filled_quantity": "0.01",
>     "builder": {
>       "address": "0x0000000000000000000000000000000000001234",
>       "fee_rate": "0.001"
>     },
>     "created_at": "2025-12-29T09:20:10Z",
>     "updated_at": "2025-12-29T09:20:10Z"
>   }
> }
> ```

**API**

Submit a signed `createOrders` operation with the Builder's account address and
an approved `fee_rate`. Use the order hashing and trading-proxy signing steps
from the quickstart above. Replace the sample salt and timestamp with fresh
values, then submit the exact operation, salt, and timestamp that you signed:

```bash Request theme={null}
curl -X POST "https://api.perpetuals.polymarket.com/v1/trade/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "op": {
      "type": "createOrders",
      "args": [
        {
          "iid": 1,
          "buy": true,
          "p": "65000",
          "qty": "0.01",
          "tif": "ioc",
          "po": false,
          "c": "7f9e4a2b6c8d0e1f1234567890abcdef",
          "builder": {
            "address": "0x0000000000000000000000000000000000001234",
            "fee_rate": "<builder_fee_rate>"
          }
        }
      ]
    },
    "sig": "<order_signature>",
    "salt": 234567890,
    "ts": 1767000010000
  }'
```

```ts OrderResponse Type theme={null}
type OrderResponse = {
  status: "ok";
  oid: number;
  coid?: string;
  builder?: {
    address: string;
    fee_rate: string;
  };
} | {
  status: "err";
  error: string;
  oid?: number;
  coid?: string;
};
```

```json OrderResponse Example theme={null}
[
  {
    "status": "ok",
    "oid": 67890,
    "coid": "7f9e4a2b6c8d0e1f1234567890abcdef",
    "builder": {
      "address": "0x0000000000000000000000000000000000001234",
      "fee_rate": "0.001"
    }
  }
]
```

Check each result in the response array. `status: "ok"` confirms acceptance and
`oid` identifies the order. `status: "err"` includes the rejection in `error`.
Acceptance does not confirm execution. Read the order state or follow private
order updates to determine the execution outcome.

### Display Trader Fees

Show the total fee charged for each execution, with exchange and Builder fees
as the breakdown. A maker rebate can make the total negative. Do not clamp it
to zero.

**TypeScript**

Read fills with `session.listFills()` on the Trader's `PerpsSession`:

```ts theme={null}
const pages = session.listFills();
const page = await pages.firstPage();
const fills = page.items;
```

Each item in `fills` has this shape. Use `totalFee` to display the total charge.

> **Output: PerpsAccountFill**
>
> ```ts PerpsAccountFill Type theme={null}
> type PerpsAccountFill = {
>   tradeId: PerpsTradeId;
>   orderId: PerpsOrderId;
>   instrumentId: PerpsInstrumentId;
>   side: PerpsSide; // long or short
>   price: DecimalString;
>   quantity: DecimalString;
>   taker: boolean;
>   fee: DecimalString; // Exchange fee; negative for a maker rebate
>   builderFee: DecimalString; // Non-negative Builder fee
>   totalFee: DecimalString; // fee + builderFee; may be negative
>   builder?: PerpsBuilderTerms;
>   feeAsset: string; // Asset in which all three fees are charged
>   previousSize: DecimalString;
>   previousEntryPrice: DecimalString;
>   pnl: DecimalString;
>   liquidation: boolean;
>   timestamp: EpochMilliseconds;
>   hash: TxHash | undefined;
> };
>
> type PerpsBuilderTerms = {
>   address: EvmAddress;
>   feeRate: DecimalString;
> };
> ```
>
> ```json PerpsAccountFill Example theme={null}
> {
>   "tradeId": 12345,
>   "orderId": 67890,
>   "instrumentId": 1,
>   "side": "long",
>   "price": "65000",
>   "quantity": "0.01",
>   "taker": true,
>   "fee": "0.26",
>   "builderFee": "0.65",
>   "totalFee": "0.91",
>   "builder": {
>     "address": "0x0000000000000000000000000000000000001234",
>     "feeRate": "0.001"
>   },
>   "feeAsset": "pUSD",
>   "previousSize": "0",
>   "previousEntryPrice": "0",
>   "pnl": "0",
>   "liquidation": false,
>   "timestamp": 1767000010000
> }
> ```

Private session fill events expose the same fee fields.

**Python**

Read fills with `session.list_fills()` on the Trader's `PerpsSession`:

```python theme={null}
pages = session.list_fills()
page = await pages.first_page()
fills = page.items
```

Each item in `fills` has this shape. Use `total_fee` to display the total charge.

> **Output: PerpsFill**
>
> ```python PerpsFill Type theme={null}
> class PerpsFill:
>     trade_id: PerpsTradeId
>     order_id: PerpsOrderId
>     instrument_id: PerpsInstrumentId
>     side: PerpsSide  # long or short
>     price: Decimal
>     quantity: Decimal
>     taker: bool
>     fee: Decimal  # Exchange fee; negative for a maker rebate
>     builder_fee: Decimal  # Non-negative Builder fee
>     total_fee: Decimal  # fee + builder_fee; may be negative
>     builder: PerpsBuilderAttribution | None = None
>     fee_asset: str  # Asset in which all three fees are charged
>     previous_size: Decimal
>     previous_entry_price: Decimal
>     pnl: Decimal
>     liquidation: bool
>     timestamp: datetime
>     hash: str | None = None
>     client_order_id: str | None = None
>
>
> class PerpsBuilderAttribution:
>     address: EvmAddress
>     fee_rate: Decimal
> ```
>
> ```json PerpsFill Example theme={null}
> {
>   "trade_id": 12345,
>   "order_id": 67890,
>   "instrument_id": 1,
>   "side": "long",
>   "price": "65000",
>   "quantity": "0.01",
>   "taker": true,
>   "fee": "0.26",
>   "builder_fee": "0.65",
>   "total_fee": "0.91",
>   "builder": {
>     "address": "0x0000000000000000000000000000000000001234",
>     "fee_rate": "0.001"
>   },
>   "fee_asset": "pUSD",
>   "previous_size": "0",
>   "previous_entry_price": "0",
>   "pnl": "0",
>   "liquidation": false,
>   "timestamp": "2025-12-29T09:20:10Z",
>   "hash": null
> }
> ```

Private session fill events expose the same fee fields.

**API**

Fetch the Trader's fills with authenticated account access:

```bash Request theme={null}
curl "https://api.perpetuals.polymarket.com/v1/account/fills" \
  -H "polymarket-proxy: <proxy_address>" \
  -H "polymarket-secret: <proxy_secret>"
```

```ts AccountTrades Type theme={null}
type AccountTrades = {
  data: Array<{
    trade_id: number;
    order_id: number;
    instrument_id: number;
    side: "long" | "short";
    price: string;
    quantity: string;
    taker: boolean;
    fee: string; // Exchange fee; negative for a maker rebate
    fee_asset: string; // Asset in which the fees are charged
    previous_size: string;
    previous_entry_price: string;
    pnl: string;
    liquidation: boolean;
    adl: boolean;
    timestamp: number;
    hash: TxHash | undefined;
    builder_fee: string; // Non-negative Builder fee
    total_fee: string; // fee + builder_fee; may be negative
    builder?: {
      address: string;
      fee_rate: string;
    };
  }>;
  more: boolean;
  cursor?: string;
};
```

```json AccountTrades Example theme={null}
{
  "data": [
    {
      "trade_id": 12345,
      "order_id": 67890,
      "instrument_id": 1,
      "side": "long",
      "price": "65000",
      "quantity": "0.01",
      "taker": true,
      "fee": "0.26",
      "builder_fee": "0.65",
      "total_fee": "0.91",
      "fee_asset": "pUSD",
      "previous_size": "0",
      "previous_entry_price": "0",
      "pnl": "0",
      "timestamp": 1767000010000,
      "liquidation": false,
      "adl": false,
      "hash": "0x",
      "builder": {
        "address": "0x0000000000000000000000000000000000001234",
        "fee_rate": "0.001"
      }
    }
  ],
  "more": false
}
```

Private WebSocket fills use the same fee amount fields and `fea` for the fee asset.

#### Rounding and PnL

Builder fees apply to eligible maker and taker executions, including voluntary
closes. Forced liquidation legs, backstop transfers, and automatic deleveraging
are excluded. An ordinary trading counterparty can still pay its saved fee.

| Case | Accounting Rule |
| - | - |
| One execution | Round the Builder fee down to the fee asset's precision: six decimal places for pUSD. |
| Multiple executions | Add the individually rounded fees. Two fees of 0.0000019 pUSD produce a total charge of 0.000002 pUSD. Discarded fractions do not carry forward. |
| Account net PnL and main PnL leaderboard | Both fee components are already included. Do not subtract them again. |
| Raw fill PnL and biggest-win rankings | Values remain before fees. |

The [exchange fee schedule](/perps/learn-about-trading/fees.md) applies separately.
Builder fees do not change exchange-fee arithmetic or the precision of fee rates.

## Builder Operations

### Check Builder Availability

Check whether a Builder can receive attributed orders.

**TypeScript**

Call `client.fetchPerpsBuilderStatus()` on an existing `SecureClient`. Set
`builderAddress` to the Builder's Perps account address:

```ts theme={null}
const status = await client.fetchPerpsBuilderStatus({
  address: builderAddress,
});

if (!status.registered || !status.enabled || !status.admissionEnabled) {
  throw new Error("Builder fees are unavailable for this account");
}
```

> **Output: PerpsBuilderStatus**
>
> ```ts PerpsBuilderStatus Type theme={null}
> type PerpsBuilderStatus = {
>   address: EvmAddress;
>   registered: boolean;
>   enabled: boolean;
>   admissionEnabled: boolean;
>   maxFeeRate: DecimalString;
> };
> ```
>
> ```json PerpsBuilderStatus Example theme={null}
> {
>   "address": "0x0000000000000000000000000000000000001234",
>   "registered": true,
>   "enabled": true,
>   "admissionEnabled": true,
>   "maxFeeRate": "0.001"
> }
> ```

**Python**

Call `client.fetch_perps_builder_status()` on an existing `AsyncSecureClient`.
Set `builder_address` to the Builder's Perps account address:

```python theme={null}
status = await client.fetch_perps_builder_status(address=builder_address)

if not (status.registered and status.enabled and status.admission_enabled):
    raise RuntimeError("Builder fees are unavailable for this account")
```

> **Output: PerpsBuilderStatus**
>
> ```python PerpsBuilderStatus Type theme={null}
> class PerpsBuilderStatus:
>     address: EvmAddress
>     registered: bool
>     enabled: bool
>     admission_enabled: bool
>     max_fee_rate: Decimal
> ```
>
> ```json PerpsBuilderStatus Example theme={null}
> {
>   "address": "0x0000000000000000000000000000000000001234",
>   "registered": true,
>   "enabled": true,
>   "admission_enabled": true,
>   "max_fee_rate": "0.001"
> }
> ```

**API**

Fetch the Builder's status:

```bash Request theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/info/builder" \
  --data-urlencode "address=<builder_account_address>"
```

```ts BuilderStatus Type theme={null}
type BuilderStatus = {
  address: string;
  registered: boolean;
  enabled: boolean;
  admission_enabled: boolean;
  max_fee_rate: string;
};
```

```json BuilderStatus Example theme={null}
{
  "address": "0x0000000000000000000000000000000000001234",
  "registered": true,
  "enabled": true,
  "admission_enabled": true,
  "max_fee_rate": "0.001"
}
```

The Builder is available when `registered`, `enabled`, and `admission_enabled`
are `true`.

### Reconcile Builder Earnings

Builders receive their full additional fee immediately in their Perps account.
The credit can be used as collateral or withdrawn under the usual account rules.
Earnings history reports settled activity. Use the account balance to determine
spendable funds.

Authenticate as the Builder account for reporting. The Trader's session used
above cannot read another account's earnings.

**TypeScript**

Use `fetchBuilderEarningsSummary()` and `listBuilderEarnings()` on a
`PerpsSession` opened by the Builder's `SecureClient`. Here, `builderSession`
is that session, created through [Authenticated
Sessions](/perps/authenticated-sessions.md#set-up-perps-access).

```ts theme={null}
import type { PerpsBuilderEarning } from "@polymarket/client";

const summary = await builderSession.fetchBuilderEarningsSummary();
const pages = builderSession.listBuilderEarnings(summary.snapshot);
const receipts: PerpsBuilderEarning[] = [];

for await (const page of pages) {
  receipts.push(...page.items);
}
```

`summary.snapshot` fixes `start`, `end`, and `asOfSequence` so every page matches
the summary. To select a window, pass `start` and `end` as Unix milliseconds to
`fetchBuilderEarningsSummary()`. Use `earningId` to deduplicate receipts.

> **Output: PerpsBuilderEarning**
>
> ```ts PerpsBuilderEarning Type theme={null}
> type PerpsBuilderEarning = {
>   earningId: PerpsBuilderEarningId;
>   tradeId: PerpsTradeId;
>   orderId: PerpsOrderId;
>   instrumentId: PerpsInstrumentId;
>   trader: EvmAddress; // Main account address
>   side: OrderSide; // "BUY" or "SELL"
>   liquidityRole: PerpsLiquidityRole; // "maker" or "taker"
>   price: DecimalString;
>   quantity: DecimalString;
>   clientOrderId?: PerpsClientOrderId;
>   timestamp: EpochMilliseconds;
>   sequence: number;
>   notional: DecimalString;
>   feeAsset: string;
>   fee: DecimalString; // Signed exchange fee in feeAsset
>   builderFee: DecimalString; // Non-negative builder fee in feeAsset
>   totalFee: DecimalString; // fee + builderFee
>   feeRate: DecimalString; // Saved builder rate as a decimal fraction
> };
> ```
>
> ```json PerpsBuilderEarning Example theme={null}
> {
>   "earningId": "3:12345:1",
>   "tradeId": 12345,
>   "orderId": 67890,
>   "instrumentId": 3,
>   "trader": "0x0000000000000000000000000000000000005678",
>   "side": "BUY",
>   "liquidityRole": "taker",
>   "price": "100",
>   "quantity": "1",
>   "clientOrderId": "7f9e4a2b6c8d0e1f1234567890abcdef",
>   "timestamp": 1767000010000,
>   "sequence": 1234567890,
>   "notional": "100",
>   "feeAsset": "pUSD",
>   "fee": "0.04",
>   "builderFee": "0.1",
>   "totalFee": "0.14",
>   "feeRate": "0.001"
> }
> ```

> **Output: PerpsBuilderEarningsSummary**
>
> ```ts PerpsBuilderEarningsSummary Type theme={null}
> type PerpsBuilderEarningsSummary = {
>   assets: PerpsBuilderEarningsAsset[];
>   traderCount: number; // Distinct traders with attributed fills in the snapshot
>   activeApprovalCount: number; // Current positive, unrevoked grants, independent of the snapshot
>   snapshot: PerpsBuilderEarningsSnapshot;
> };
>
> type PerpsBuilderEarningsAsset = {
>   feeAsset: string;
>   fillCount: number;
>   notional: DecimalString;
>   builderFee: DecimalString;
> };
>
> type PerpsBuilderEarningsSnapshot = {
>   start: EpochMilliseconds;
>   end: EpochMilliseconds;
>   asOfSequence: number;
> };
> ```
>
> ```json PerpsBuilderEarningsSummary Example theme={null}
> {
>   "assets": [
>     {
>       "feeAsset": "pUSD",
>       "fillCount": 1,
>       "notional": "100",
>       "builderFee": "0.1"
>     }
>   ],
>   "traderCount": 1,
>   "activeApprovalCount": 2,
>   "snapshot": {
>     "start": 1767000000000,
>     "end": 1767000060000,
>     "asOfSequence": 1234567890
>   }
> }
> ```

**Python**

Use `fetch_builder_earnings_summary()` and `list_builder_earnings()` on a
`PerpsSession` opened by the Builder's `AsyncSecureClient`. Here,
`builder_session` is that session, created through [Authenticated
Sessions](/perps/authenticated-sessions.md#set-up-perps-access).

```python theme={null}
from polymarket.models.perps import PerpsBuilderEarning

summary = await builder_session.fetch_builder_earnings_summary()
snapshot = summary.snapshot
pages = builder_session.list_builder_earnings(
    start=snapshot.start,
    end=snapshot.end,
    as_of_sequence=snapshot.as_of_sequence,
)
receipts: list[PerpsBuilderEarning] = []

async for page in pages:
    receipts.extend(page.items)
```

`summary.snapshot` fixes the window and indexed cutoff so every page matches
the summary. To select a window, pass `start` and `end` as datetimes or Unix
milliseconds to `fetch_builder_earnings_summary()`. Use `earning_id` to
deduplicate receipts. Each page also retains its `snapshot`, including empty pages.

> **Output: PerpsBuilderEarning**
>
> ```python PerpsBuilderEarning Type theme={null}
> class PerpsBuilderEarning:
>     earning_id: str
>     trade_id: PerpsTradeId
>     order_id: PerpsOrderId
>     instrument_id: PerpsInstrumentId
>     trader: EvmAddress  # Main account address
>     side: OrderSide  # BUY or SELL
>     liquidity_role: PerpsLiquidityRole  # maker or taker
>     price: Decimal
>     quantity: Decimal
>     client_order_id: str | None = None
>     timestamp: datetime
>     sequence: int
>     notional: Decimal
>     fee_asset: str
>     fee: Decimal  # Signed exchange fee in fee_asset
>     builder_fee: Decimal  # Non-negative builder fee in fee_asset
>     total_fee: Decimal  # fee + builder_fee
>     fee_rate: Decimal  # Saved builder rate as a decimal fraction
> ```
>
> ```json PerpsBuilderEarning Example theme={null}
> {
>   "earning_id": "3:12345:1",
>   "trade_id": 12345,
>   "order_id": 67890,
>   "instrument_id": 3,
>   "trader": "0x0000000000000000000000000000000000005678",
>   "side": "BUY",
>   "liquidity_role": "taker",
>   "price": "100",
>   "quantity": "1",
>   "client_order_id": "7f9e4a2b6c8d0e1f1234567890abcdef",
>   "timestamp": "2025-12-29T09:20:10Z",
>   "sequence": 1234567890,
>   "notional": "100",
>   "fee_asset": "pUSD",
>   "fee": "0.04",
>   "builder_fee": "0.1",
>   "total_fee": "0.14",
>   "fee_rate": "0.001"
> }
> ```

> **Output: PerpsBuilderEarningsSummary**
>
> ```python PerpsBuilderEarningsSummary Type theme={null}
> class PerpsBuilderEarningsSummary:
>     assets: tuple[PerpsBuilderEarningsAsset, ...]
>     trader_count: int  # Distinct traders with attributed fills in the snapshot
>     active_approval_count: int  # Current positive, unrevoked grants, independent of the snapshot
>     snapshot: PerpsBuilderEarningsSnapshot
>
>
> class PerpsBuilderEarningsAsset:
>     fee_asset: str
>     fill_count: int
>     notional: Decimal
>     builder_fee: Decimal
>
>
> class PerpsBuilderEarningsSnapshot:
>     start: datetime
>     end: datetime
>     as_of_sequence: int
> ```
>
> ```json PerpsBuilderEarningsSummary Example theme={null}
> {
>   "assets": [
>     {
>       "fee_asset": "pUSD",
>       "fill_count": 1,
>       "notional": "100",
>       "builder_fee": "0.1"
>     }
>   ],
>   "trader_count": 1,
>   "active_approval_count": 2,
>   "snapshot": {
>     "start": "2025-12-29T09:20:00Z",
>     "end": "2025-12-29T09:21:00Z",
>     "as_of_sequence": 1234567890
>   }
> }
> ```

**API**

Get the Builder account's proxy address and secret through [Authenticated Sessions](/perps/authenticated-sessions.md#set-up-perps-access).
Fetch its earnings summary:

```bash Request theme={null}
curl "https://api.perpetuals.polymarket.com/v1/account/builder-earnings-summary" \
  -H "polymarket-proxy: <builder_proxy_address>" \
  -H "polymarket-secret: <builder_proxy_secret>"
```

```ts BuilderEarningsSummary Type theme={null}
type BuilderEarningsSummary = {
  data: Array<{
    fee_asset: string; // Asset in which the fees are charged
    fill_count: number;
    notional: string;
    builder_fee: string; // Non-negative Builder fee
  }>;
  start_timestamp: number;
  end_timestamp: number;
  as_of_sequence: number;
  trader_count: number;
  active_approval_count: number;
};
```

```json BuilderEarningsSummary Example theme={null}
{
  "data": [
    {
      "fee_asset": "pUSD",
      "fill_count": 2,
      "notional": "200",
      "builder_fee": "0.2"
    }
  ],
  "start_timestamp": 1767000000000,
  "end_timestamp": 1767000060000,
  "as_of_sequence": 1234567891,
  "trader_count": 1,
  "active_approval_count": 2
}
```

The response contains totals in `data`, plus `start_timestamp`, `end_timestamp`,
`as_of_sequence`, `trader_count`, and `active_approval_count`. Pass the returned
window and cutoff to the receipts request:

```bash Request theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/builder-earnings" \
  -H "polymarket-proxy: <builder_proxy_address>" \
  -H "polymarket-secret: <builder_proxy_secret>" \
  --data-urlencode "start_timestamp=<start_timestamp>" \
  --data-urlencode "end_timestamp=<end_timestamp>" \
  --data-urlencode "as_of_sequence=<as_of_sequence>"
```

```ts BuilderEarnings Type theme={null}
type BuilderEarnings = {
  data: Array<{
    earning_id: string;
    trade_id: number;
    order_id: number;
    instrument_id: number;
    side: "maker" | "taker";
    timestamp: number;
    sequence: number;
    notional: string;
    fee_asset: string; // Asset in which the fees are charged
    builder_fee: string; // Non-negative Builder fee
    fee_rate: string;
    trader: string;
    buy: boolean;
    price: string;
    quantity: string;
    client_order_id?: string;
    fee: string; // Exchange fee; negative for a maker rebate
    total_fee: string; // fee + builder_fee; may be negative
  }>;
  more: boolean;
  cursor?: string;
  start_timestamp: number;
  end_timestamp: number;
  as_of_sequence: number;
};
```

```json BuilderEarnings Example theme={null}
{
  "data": [
    {
      "earning_id": "3:12345:1",
      "trade_id": 12345,
      "order_id": 67890,
      "instrument_id": 3,
      "trader": "0x0000000000000000000000000000000000005678",
      "side": "taker",
      "buy": true,
      "price": "100",
      "quantity": "1",
      "client_order_id": "7f9e4a2b6c8d0e1f1234567890abcdef",
      "timestamp": 1767000010000,
      "sequence": 1234567890,
      "notional": "100",
      "fee_asset": "pUSD",
      "fee": "0.04",
      "builder_fee": "0.1",
      "total_fee": "0.14",
      "fee_rate": "0.001"
    }
  ],
  "more": true,
  "cursor": "<opaque_cursor>",
  "start_timestamp": 1767000000000,
  "end_timestamp": 1767000060000,
  "as_of_sequence": 1234567891
}
```

Follow `cursor` while `more` is `true`. The cursor preserves the window and
cutoff. Send it unchanged to the same endpoint:

```bash Request theme={null}
curl -G "https://api.perpetuals.polymarket.com/v1/account/builder-earnings" \
  -H "polymarket-proxy: <builder_proxy_address>" \
  -H "polymarket-secret: <builder_proxy_secret>" \
  --data-urlencode "cursor=<cursor>"
```

```ts BuilderEarnings Type theme={null}
type BuilderEarnings = {
  data: Array<{
    earning_id: string;
    trade_id: number;
    order_id: number;
    instrument_id: number;
    side: "maker" | "taker";
    timestamp: number;
    sequence: number;
    notional: string;
    fee_asset: string; // Asset in which the fees are charged
    builder_fee: string; // Non-negative Builder fee
    fee_rate: string;
    trader: string;
    buy: boolean;
    price: string;
    quantity: string;
    client_order_id?: string;
    fee: string; // Exchange fee; negative for a maker rebate
    total_fee: string; // fee + builder_fee; may be negative
  }>;
  more: boolean;
  cursor?: string;
  start_timestamp: number;
  end_timestamp: number;
  as_of_sequence: number;
};
```

```json BuilderEarnings Example theme={null}
{
  "data": [
    {
      "earning_id": "3:12346:1",
      "trade_id": 12346,
      "order_id": 67891,
      "instrument_id": 3,
      "trader": "0x0000000000000000000000000000000000005678",
      "side": "taker",
      "buy": true,
      "price": "100",
      "quantity": "1",
      "client_order_id": "8f9e4a2b6c8d0e1f1234567890abcdef",
      "timestamp": 1767000020000,
      "sequence": 1234567891,
      "notional": "100",
      "fee_asset": "pUSD",
      "fee": "0.04",
      "builder_fee": "0.1",
      "total_fee": "0.14",
      "fee_rate": "0.001"
    }
  ],
  "more": false,
  "start_timestamp": 1767000000000,
  "end_timestamp": 1767000060000,
  "as_of_sequence": 1234567891
}
```

Use `earning_id` to deduplicate receipts.

`trader` is the main account address, not its trading proxy. `side` identifies
maker or taker. `buy` identifies direction. Receipts also carry order, trade and
instrument IDs, `fee_rate`, `fee_asset`, `timestamp`, and `sequence`.
`client_order_id` is present when the order supplied one.

The summary's `data` contains totals by fee asset. `trader_count` counts distinct
accounts with attributed fills in the window and cutoff. `active_approval_count`
counts current positive, unrevoked grants independently of that window.
Timestamps use Unix milliseconds.

#### Reporting Rules

| Rule | Effect |
| - | - |
| History window | The default is seven days. Each request supports up to 90 days. Select earlier windows for older earnings. |
| Indexed cutoff | Reports include a complete indexed history prefix. They can lag live balance updates. |
| Receipt identity | Each attributed fill side has a receipt, even when its fee rounds to zero. Two sides paying the same Builder produce separate receipts. |
| Summary counts | Volume and fill counts include zero-fee receipts. Distinct Traders count once across markets, assets, and fill sides. |
| Active approvals | Disabling a Builder does not erase its existing grants. |

Exchange fee schedules, referral fee totals, and exchange revenue exclude
Builder charges.

### Follow Live Builder Fills

Use live receipts to update earnings as trades execute. Authenticate as the
Builder account, as in the reporting examples above. The feed covers all Traders
and markets attributed to that account.

**TypeScript**

Open a session with `includeBuilderFills: true` on the Builder's
`SecureClient`. Here, `builderClient` authenticates the Builder account through
[Authenticated Sessions](/perps/authenticated-sessions.md#set-up-perps-access).

```ts theme={null}
const builderSession = await builderClient.openPerpsSession({
  includeBuilderFills: true,
});
try {
  for await (const event of builderSession) {
    if (event.type === "builderFill") {
      const receipts = event.payload;
      // Persist receipts, deduplicating by earningId.
    } else if (event.type === "resync") {
      // Reconcile earnings using the recovery workflow below.
    }
    // Handle other account events here.
  }
} finally {
  await builderSession.close();
}
```

`builderFill` is part of `PerpsSessionEvent`, alongside orders, fills, balances,
and `resync` events. There is one session iterator and one connection lifecycle.
`includeBuilderFills` defaults to `false`. Setting `builderAttribution` does
not select whose receipts are delivered. Receipts belong to the authenticated
Builder account.

> **Output: PerpsBuilderFillUpdateEvent**
>
> ```ts PerpsBuilderFillUpdateEvent Type theme={null}
> type PerpsBuilderFillUpdateEvent = {
>   type: "builderFill";
>   channel: "builderFills";
>   timestamp: EpochMilliseconds;
>   sequence: number;
>   payload: PerpsBuilderEarning[];
> };
> ```
>
> ```json PerpsBuilderFillUpdateEvent Example theme={null}
> {
>   "type": "builderFill",
>   "channel": "builderFills",
>   "timestamp": 1767000010000,
>   "sequence": 1234567890,
>   "payload": [
>     {
>       "earningId": "3:12345:1",
>       "tradeId": 12345,
>       "orderId": 67890,
>       "instrumentId": 3,
>       "trader": "0x0000000000000000000000000000000000005678",
>       "side": "BUY",
>       "liquidityRole": "taker",
>       "price": "100",
>       "quantity": "1",
>       "clientOrderId": "7f9e4a2b6c8d0e1f1234567890abcdef",
>       "timestamp": 1767000010000,
>       "sequence": 1234567890,
>       "notional": "100",
>       "feeAsset": "pUSD",
>       "fee": "0.04",
>       "builderFee": "0.1",
>       "totalFee": "0.14",
>       "feeRate": "0.001"
>     }
>   ]
> }
> ```

**Python**

Open a session with `include_builder_fills=True` on the Builder's
`AsyncSecureClient`. Here, `builder_client` authenticates the Builder account
through [Authenticated Sessions](/perps/authenticated-sessions.md#set-up-perps-access).

```python theme={null}
async with await builder_client.open_perps_session(include_builder_fills=True) as builder_session:
    async for event in builder_session:
        if event.type == "builder_fill":
            receipts = event.payload
            # Persist receipts, deduplicating by earning_id.
        elif event.type == "resync":
            # Reconcile earnings using the recovery workflow below.
            pass
        # Handle other account events here.
```

`builder_fill` is part of `PerpsSessionEvent`, alongside orders, fills,
balances, and `resync` events. There is one session iterator and one connection
lifecycle. `include_builder_fills` defaults to `False`. Setting
`builder_attribution` does not select whose receipts are delivered. Receipts
belong to the authenticated Builder account.

> **Output: PerpsBuilderFillEvent**
>
> ```python PerpsBuilderFillEvent Type theme={null}
> class PerpsBuilderFillEvent:
>     type: Literal["builder_fill"] = "builder_fill"
>     channel: Literal["builderFills"] = "builderFills"
>     timestamp: datetime
>     sequence: int
>     payload: tuple[PerpsBuilderEarning, ...]
> ```
>
> ```json PerpsBuilderFillEvent Example theme={null}
> {
>   "type": "builder_fill",
>   "channel": "builderFills",
>   "timestamp": "2025-12-29T09:20:10Z",
>   "sequence": 1234567890,
>   "payload": [
>     {
>       "earning_id": "3:12345:1",
>       "trade_id": 12345,
>       "order_id": 67890,
>       "instrument_id": 3,
>       "trader": "0x0000000000000000000000000000000000005678",
>       "side": "BUY",
>       "liquidity_role": "taker",
>       "price": "100",
>       "quantity": "1",
>       "client_order_id": "7f9e4a2b6c8d0e1f1234567890abcdef",
>       "timestamp": "2025-12-29T09:20:10Z",
>       "sequence": 1234567890,
>       "notional": "100",
>       "fee_asset": "pUSD",
>       "fee": "0.04",
>       "builder_fee": "0.1",
>       "total_fee": "0.14",
>       "fee_rate": "0.001"
>     }
>   ]
> }
> ```

**API**

Connect and authenticate as the Builder using the credentials from the earnings
reporting example.

**1. Authenticate the Connection**

Open a WebSocket connection to `wss://ws.perpetuals.polymarket.com/v1/ws`.
Send the Builder's proxy address and secret in an authentication frame:

```json Request theme={null}
{
  "id": 1,
  "req": "post",
  "op": {
    "type": "auth",
    "args": {
      "proxy": "<builder_proxy_address>",
      "secret": "<builder_proxy_secret>"
    }
  }
}
```

```ts AuthResponse Type theme={null}
type AuthResponse = {
  id?: number;
  data: {
    status: "ok";
    pod?: string;
  } | {
    status: "err";
    error: string;
  };
};
```

```json AuthResponse Example theme={null}
{
  "id": 1,
  "data": {
    "status": "ok"
  }
}
```

Wait for the matching response with `data.status: "ok"` before subscribing.
See [Session Lifecycle](/perps/authenticated-sessions.md#session-lifecycle) for
connection keepalive and authentication errors.

**2. Subscribe to Receipts**

Send the subscription frame on the authenticated connection:

```json Request theme={null}
{
  "id": 2,
  "req": "sub",
  "chs": [
    "builderFills"
  ]
}
```

```ts BuilderFillsSubscribeResponse Type theme={null}
type BuilderFillsSubscribeResponse = {
  id?: number;
  data: Array<{
    status: "ok";
  } | {
    status: "err";
    error: string;
    arts?: number;
    ts?: number;
    ref?: string;
  }>;
};
```

```json BuilderFillsSubscribeResponse Example theme={null}
{
  "id": 2,
  "data": [
    {
      "status": "ok"
    }
  ]
}
```

Updates carry a `data` array with the same receipt fields and `earning_id`
values as history, including zero-fee fills. The channel name cannot select
another Builder's account. See [Builder Fills](/api-reference/wss/perps-builder-fills.md)
for the message schema. After a disconnect, authenticate and subscribe again.

> **Output: BuilderFillsUpdate**
>
> ```ts BuilderFillsUpdate Type theme={null}
> type BuilderFillsUpdate = {
>   ch: string;
>   ts: number;
>   ets: number;
>   sq: number;
>   data: Array<{
>     earning_id: string;
>     trade_id: number;
>     order_id: number;
>     instrument_id: number;
>     trader: string;
>     buy: boolean;
>     price: string;
>     quantity: string;
>     client_order_id?: string;
>     side: "maker" | "taker";
>     timestamp: number;
>     sequence: number;
>     notional: string;
>     fee_asset: string; // Asset in which the fees are charged
>     fee: string; // Exchange fee; negative for a maker rebate
>     builder_fee: string; // Non-negative Builder fee
>     total_fee: string; // fee + builder_fee; may be negative
>     fee_rate: string;
>   }>;
> };
> ```
>
> ```json BuilderFillsUpdate Example theme={null}
> {
>   "ch": "builderFills",
>   "ts": 1767225600000,
>   "ets": 1767225600000,
>   "sq": 1234567890,
>   "data": [
>     {
>       "earning_id": "1:1:0",
>       "trade_id": 1,
>       "order_id": 1234567890,
>       "instrument_id": 1,
>       "trader": "0x1234567890abcdef1234567890abcdef12345678",
>       "buy": true,
>       "price": "100.00",
>       "quantity": "10.00",
>       "client_order_id": "550e8400e29b41d4a716446655440000",
>       "side": "maker",
>       "sequence": 1234567890,
>       "notional": "1000.00",
>       "timestamp": 1767225600000,
>       "fee_asset": "pUSD",
>       "fee": "0.125",
>       "builder_fee": "1.00",
>       "total_fee": "1.125",
>       "fee_rate": "0.001"
>     }
>   ]
> }
> ```

#### Recover Missed Receipts

The feed has no initial history snapshot or exactly-once delivery guarantee.
Its sequence numbers can skip values because other account activity shares the
sequence. A gap alone does not prove receipts were lost. Use history to recover
missed receipts after connecting or reconnecting:

**1. Buffer Live Receipts**

Start consuming the live feed and buffer receipts while fetching history.

**2. Read Overlapping History**

Select a window that starts before your last stored receipt. Fetch every
page at the same indexed cutoff, using the reporting workflow above.

**3. Merge Receipts**

Deduplicate history and buffered receipts by their receipt identity. Keep
the identity with your stored record so repeated recovery does not count an
execution twice.

**4. Check Indexing Progress**

History can lag the feed. Repeat the overlapping read with a newer cutoff
until indexing covers the interval being recovered. Do not treat the largest
sequence on a receipt as the complete indexed cutoff.

### Handle Order Errors

If an order is rejected because the Trader has not approved the Builder fee,
request approval for that fee before placing another order. The examples below
approve 10 basis points (0.1%) after the Trader accepts the fee.

**TypeScript**

`session.placeOrder()` throws `RequestRejectedError` with the message
`builder_approval_required` when approval is missing, revoked, or does not cover
the order's Builder fee. Check `error.message` and call `approveBuilderFee`
with the Builder address and the agreed rate:

```ts theme={null}
import { OrderSide, PerpsTimeInForce, RequestRejectedError } from "@polymarket/client";

try {
  await session.placeOrder({
    instrumentId,
    side: OrderSide.BUY,
    quantity: "0.01",
    timeInForce: PerpsTimeInForce.IOC,
  });
} catch (error) {
  if (
    !(error instanceof RequestRejectedError) ||
    error.message !== "builder_approval_required"
  ) {
    throw error;
  }

  await session.approveBuilderFee({
    builderAddress,
    maxFeeRate: "0.001", // 10 bps
  });
}
```

> **Output: PerpsBuilderApproval**
>
> ```ts PerpsBuilderApproval Type theme={null}
> type PerpsBuilderApproval = {
>   trader: EvmAddress;
>   builder: EvmAddress;
>   maxFeeRate: DecimalString; // Zero revokes consent
>   approvalVersion: number;
>   timestamp: EpochMilliseconds;
>   sequence: number;
> };
> ```
>
> ```json PerpsBuilderApproval Example theme={null}
> {
>   "trader": "0x0000000000000000000000000000000000005678",
>   "builder": "0x0000000000000000000000000000000000001234",
>   "maxFeeRate": "0.001",
>   "approvalVersion": 1,
>   "timestamp": 1767000000000,
>   "sequence": 1234567889
> }
> ```

Submit a new order after approval succeeds. For `session.postOrders()`, inspect
each result's `status` and `error` for the same rejection.

**Python**

`session.place_order()` raises `RequestRejectedError` with the message
`builder_approval_required` when approval is missing, revoked, or does not cover
the order's Builder fee. Check `str(error)` and call `approve_builder_fee`
with the Builder address and the agreed rate:

```python theme={null}
from polymarket.errors import RequestRejectedError

try:
    await session.place_order(
        instrument_id=instrument_id,
        side="BUY",
        quantity="0.01",
        time_in_force="ioc",
    )
except RequestRejectedError as error:
    if str(error) != "builder_approval_required":
        raise

    await session.approve_builder_fee(
        builder_address=builder_address,
        max_fee_rate="0.001",  # 10 bps
    )
```

> **Output: PerpsBuilderApproval**
>
> ```python PerpsBuilderApproval Type theme={null}
> class PerpsBuilderApproval:
>     trader: EvmAddress
>     builder: EvmAddress
>     max_fee_rate: Decimal  # Zero revokes consent
>     approval_version: int
>     timestamp: datetime
>     sequence: int
> ```
>
> ```json PerpsBuilderApproval Example theme={null}
> {
>   "trader": "0x0000000000000000000000000000000000005678",
>   "builder": "0x0000000000000000000000000000000000001234",
>   "max_fee_rate": "0.001",
>   "approval_version": 1,
>   "timestamp": "2025-12-29T09:20:00Z",
>   "sequence": 1234567889
> }
> ```

Submit a new order after approval succeeds. For `session.post_orders()`, inspect
each result's `status` and `error` for the same rejection.

**API**

Check each order result for `status: "err"` and
`error: "builder_approval_required"`:

> **Output: OrderResponse**
>
> ```ts OrderResponse Type theme={null}
> type OrderResponse = {
>   status: "ok";
>   oid: number;
>   coid?: string;
>   builder?: {
>     address: string;
>     fee_rate: string;
>   };
> } | {
>   status: "err";
>   error: string;
>   oid?: number;
>   coid?: string;
> };
> ```
>
> ```json OrderResponse Example theme={null}
> [
>   {
>     "status": "err",
>     "error": "builder_approval_required"
>   }
> ]
> ```

This means approval is missing, revoked, or does not cover the order's Builder
fee. Use the owner-signed `approveBuilder` operation in the Grant Approval
workflow with the Builder address, the next approval version, and the agreed
`max_fee_rate`. For 10 basis points, use `"0.001"`. Submit a new order after the
approval succeeds.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.