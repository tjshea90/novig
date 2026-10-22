> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Onchain Position Data

> Index balances, trades, and resolution across both position systems

Polymarket outcome balances live in one of two ERC-1155 contracts. Use the
market `version` returned by Gamma to select the correct section below. Field
presence is not a reliable discriminator because a CTF market can also appear
as a leg in a PositionManager workflow.

| Gamma `version` | Position system | Outcome ID | ERC-1155 ledger | Exchange domain version |
| - | - | - | - | - |
| `"v2"` | Polymarket V2 | Outcome `positionId` | PositionManager | `"3"` |
| `"v1"` | CTF | Outcome `tokenId` | Conditional Tokens | `"2"` |

See [Discover Markets](/market-data/discover-markets.md) for the discovery
workflow and [Contracts](/resources/contracts.md) for Polygon addresses.

## CTF

### Derive CTF Identifiers

Conditional Tokens derives each identifier from the condition and collateral
path. Use the deployed contract helpers when possible.

| Identifier | Derivation |
| - | - |
| Condition ID | `keccak256(abi.encodePacked(oracle, questionId, outcomeSlotCount))` |
| Collection ID | `getCollectionId(parentCollectionId, conditionId, indexSet)` |
| Position ID | `getPositionId(collateralToken, collectionId)` |

A CTF position can contain more than one condition through its parent
collection. The `indexSet` selects the represented outcomes. For a binary
condition, the two singleton collections use index sets `1` and `2`.

Conditional Tokens does not define a platform-wide event identifier. Negative
risk grouping is maintained by the Neg Risk Adapter. Gamma provides the event
and market identifiers used by application integrations.

### Index CTF Balances and Lifecycle Events

Build balances from `TransferSingle` and `TransferBatch` events emitted by
Conditional Tokens. Build operator approvals from `ApprovalForAll`.

| Event shape | Indexing action |
| - | - |
| `from` is the zero address | Increase the holder balance and indexed supply |
| `to` is the zero address | Decrease the holder balance and indexed supply |
| Both addresses are nonzero | Move the balance between holders |
| `ApprovalForAll` | Update the owner and operator approval pair |

Conditional Tokens does not expose a general total-supply read for every
outcome token. Start from the deployment block or another verified checkpoint
if your indexer needs complete historical balances or supply.

Use these lifecycle events as transaction context around the ERC-1155 balance
changes:

| Workflow | Events |
| - | - |
| Prepare | `ConditionPreparation` and `MarketPrepared` |
| Split | `PositionSplit` |
| Merge | `PositionsMerge` |
| Convert | `PositionsConverted` |
| Redeem | `PayoutRedemption` |
| Resolve | `OutcomeReported` and `ConditionResolution` |

ERC-1155 transfer events remain the canonical input for balances, minting,
burning, and supply. Do not count a lifecycle event and its underlying transfer
as two balance changes.

### Attribute CTF Exchange Fills

The CTF exchanges emit `OrderFilled`, `OrdersMatched`, and `FeeCharged`. The
signed order field is named `tokenId` and contains the CTF outcome token ID
selected during market discovery.

`OrderFilled` includes the signed `builder` value and `metadata`. Record the
exchange address or EIP-712 domain version `"2"` with each order. This keeps
fills attributable to the correct position system when both systems use the
same order shape.

### Track CTF Resolution and Redemptions

Conditional Tokens stores `payoutNumerators` and `payoutDenominator`. The
payout fraction for one outcome is its numerator divided by the condition
denominator.

AutoRedeemer identifies the source of an automated CTF redemption with one of
these events:

| Event | Balance source |
| - | - |
| `BinaryRedemption` | Binary Conditional Tokens position |
| `NegRiskRedemption` | Negative risk Conditional Tokens position |

The ledger also emits its normal redemption, burn, and transfer events. Use the
AutoRedeemer event to attribute the automated action and the ledger events to
update balances and supply.

## Polymarket Protocol V2

### Decode Polymarket Protocol V2 Position IDs

A Polymarket Protocol V2 Position ID encodes its module, event, condition, and outcome.
The same value can be reduced to a Condition ID or Event ID by clearing its
lower fields.

```text theme={null}
[moduleId | baseHash | arity | reserved | resolutionChain | conditionIndex | outcomeIndex]
  8 bits    128 bits  16 bits   64 bits       16 bits          16 bits          8 bits
```

| Field | Bits | Purpose |
| - | - | - |
| `moduleId` | 248 to 255 | Routes the position to its module |
| `baseHash` | 120 to 247 | Identifies the source event or question data |
| `arity` | 104 to 119 | Stores the real condition count for negative risk events |
| `reserved` | 40 to 103 | Reserved event identity bits |
| `resolutionChain` | 24 to 39 | Identifies the chain allowed to resolve the condition |
| `conditionIndex` | 8 to 23 | Selects a condition within an event |
| `outcomeIndex` | 0 to 7 | Selects the condition outcome |

Current module IDs are:

| Module ID | Module |
| - | - |
| `1` | BinaryModule |
| `2` | NegRiskModule |
| `3` | CombinatorialModule |

The native Polymarket Protocol V2 identifier helpers derive identifiers in this order:

| Identifier | Derivation |
| - | - |
| Base hash | `keccak256(abi.encode(moduleId, data))`, truncated to its low 128 bits in the encoded ID |
| Event ID | Encodes the module ID, base hash, arity, and resolution chain with the lower 24 bits clear |
| Condition ID | Adds `conditionIndex` to its Event ID and keeps the outcome byte clear |
| Position ID | Adds `outcomeIndex` to its Condition ID |

Migration uses precomputed legacy identifiers instead of the native base-hash
derivation. Binary migration passes the legacy CTF Condition ID directly as the
base-hash input. Negative risk migration passes the legacy event ID directly as
the Event ID base-hash input, then derives its Condition IDs by index. Do not
hash either legacy identifier again. Treat `MigrationConditionRegistered` as
the authoritative CTF-to-Polymarket Protocol V2 Condition ID mapping and `EventPrepared`
as the authoritative legacy-to-Polymarket Protocol V2 Event ID mapping.

Polymarket Protocol V2 represents a Condition ID as `bytes31` and an Event ID as `bytes29`.
Compatibility boundaries that accept or return `bytes32` use the same values
right-padded with zero bytes. Preserve the native widths when decoding typed ABI
values, and pad only when the target ABI requires `bytes32`.

Binary conditions have arity `0` and condition index `0`, so their Condition ID
is bit-equivalent to their Event ID.

Use these masks when decoding a Position ID:

```text theme={null}
moduleId       = positionId >> 248
outcomeIndex   = positionId & 0xff
conditionId    = positionId with bits 0 to 7 cleared
eventId        = positionId with bits 0 to 23 cleared
conditionIndex = (positionId >> 8) & 0xffff
arity          = (positionId >> 104) & 0xffff
```

The other binary outcome is derived by replacing `outcomeIndex` with `0` for
YES or `1` for NO. A canonical Condition ID has a zero outcome byte. A canonical
Event ID has zero condition and outcome fields.

### Index PositionManager Balances and Lifecycle Events

Build balances from `TransferSingle` and `TransferBatch` events emitted by
PositionManager. Build operator approvals from `ApprovalForAll`.

| Event shape | Indexing action |
| - | - |
| `from` is the zero address | Increase the holder balance and indexed supply |
| `to` is the zero address | Decrease the holder balance and indexed supply |
| Both addresses are nonzero | Move the balance between holders |
| `ApprovalForAll` | Update the owner and operator approval pair |

PositionManager does not expose a general total-supply read for every outcome
token. Start from the deployment block or another verified checkpoint if your
indexer needs complete historical balances or supply.

Use these lifecycle events as transaction context around the ERC-1155 balance
changes:

| Workflow | Module scope | Events |
| - | - | - |
| Prepare | NegRiskModule migration | `EventPrepared` |
| Prepare | CombinatorialModule | `CombinatorialConditionPrepared` |
| Split | BinaryModule, NegRiskModule, or Combo | `PositionsSplit`, `HorizontalSplit`, or combinatorial split events |
| Merge | BinaryModule, NegRiskModule, or Combo | `PositionsMerged`, `HorizontalMerge`, or combinatorial merge events |
| Convert | NegRiskModule | `PositionConverted` |
| Redeem | BinaryModule, NegRiskModule, or Combo | `PositionRedeemed` |
| Resolve | BinaryModule or NegRiskModule | `ResultReported` and `ConditionResolved` |
| Bridge | BinaryModule or NegRiskModule | `BridgePositionMinted` and `BridgePositionsBurned` |

Native BinaryModule and NegRiskModule identifiers have no preparation event.
CombinatorialModule has its own operation-specific conversion events in
addition to those summarized above, and Combo positions are not bridgeable.

The Router also emits events for routed splits, merges, conversions,
redemptions, and collateral returns. Store these events as call context if
needed. Do not apply their amounts as additional ERC-1155 balance updates.

CombinatorialModule emits operation-specific events for changes within a Combo,
including splits, merges, conversions, extraction, injection, compression, and
wrapping. Index the deployed module ABI when those changes must be reconstructed.

ERC-1155 transfer events remain the canonical input for balances, minting,
burning, and supply. Do not count a lifecycle event and its underlying transfer
as two balance changes.

### Attribute Polymarket Protocol V2 Exchange Fills

The Polymarket Protocol V2 Exchange emits `OrderFilled`, `OrdersMatched`, and
`FeeCharged`. The signed order field is named `tokenId` and contains the
Position ID selected during market discovery.

`OrderFilled` includes the signed `builder` value and `metadata`. Record the
exchange address or EIP-712 domain version `"3"` with each order. This keeps
fills attributable to the correct position system when both systems use the
same order shape.

### Track Polymarket Protocol V2 Resolution and Redemptions

BinaryModule and NegRiskModule store result vectors in parts per million. The
result denominator is `1,000,000`. `OracleAggregator` events describe reporter
consensus and disputes. A `ConditionResolved` event from BinaryModule or
NegRiskModule records the result that controls redemption.

CombinatorialModule stores each Combo condition's BinaryModule and NegRiskModule
legs, not a separate result vector. Determine a Combo position's payout from
the stored legs and their underlying module results. CombinatorialModule does
not emit `ConditionResolved`.

For native Polymarket Protocol V2 negative risk events, not every result needs a separate
stored value:

* `RemainingConditionsDerivableAsNo` means the remaining real conditions can be
  derived as NO after a YES result reaches the full denominator.
* `SyntheticConditionDerivableAsYes` means the synthetic Other condition can be
  derived as YES after every real condition resolves NO.

An indexer that displays all outcomes must apply these derivation rules instead
of waiting for one `ConditionResolved` event per derived result.

Do not derive a sibling-NO result for a registered negative risk migration
condition. Even if `RemainingConditionsDerivableAsNo` is emitted for its event,
`NegRiskModule.getResult` excludes registered migration conditions from that
derivation. Wait for each migrated real condition's stored CTF-backed result,
recorded by `ConditionResolved` and `MigrationResolved`. The synthetic Other
condition has no CTF mapping and remains derivable after all real conditions
resolve NO.

AutoRedeemer emits `Redemption` for an automated PositionManager redemption.
The ledger also emits its normal redemption, burn, and transfer events. Use the
AutoRedeemer event to attribute the automated action and the ledger events to
update balances and supply.

### Track Migration from CTF

Migration moves a registered CTF balance into the corresponding PositionManager
balance. Index these events from BinaryModule and NegRiskModule:

| Event | Meaning |
| - | - |
| `MigrationConditionRegistered` | Links a PositionManager Condition ID to its CTF condition ID |
| `EventPrepared` with `legacyEventId` | Links a negative risk Event ID to its CTF event |
| `PositionMigrated` | Records the holder, new Condition ID, Position ID, outcome index, and amount |
| `MigrationResolved` | Records the CTF payouts and normalized module result |
| `LegacyCollateralSettled` | Records collateral released by CTF redemption or complementary YES/NO merges |

A migrated condition resolves from the registered CTF payout. A Polymarket
Protocol V2 resolver cannot replace that result. A bridge report first derives
the result from CTF and must match it. The synthetic Other condition in a
negative risk event has no CTF mapping and is derived by NegRiskModule.

Migration also emits CTF transfers and PositionManager mint events. Use the
ERC-1155 events to update balances, then use `PositionMigrated` to connect the
old and new identifiers.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.