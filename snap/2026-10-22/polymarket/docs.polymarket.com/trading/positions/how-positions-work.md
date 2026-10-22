> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# How Positions Work

> Understand Polymarket outcome positions, ledgers, and identifiers.

Polymarket represents tradeable outcomes as ERC-1155 balances. Every binary
market has a YES position and a NO position, but the contract that owns those
balances depends on the market's position system.

## Positions Ledgers

| Position system | ERC-1155 ledger | Market logic | Outcome identifier |
| - | - | - | - |
| Polymarket V2 | PositionManager | BinaryModule, NegRiskModule, or CombinatorialModule | Position ID |
| CTF | Conditional Tokens | Standard CTF or Neg Risk Adapter | CTF token ID |

Do not send a CTF token ID to PositionManager or a Position ID to Conditional
Tokens. Market discovery provides the identifiers required by each workflow.

## Position Lifecycle

Both systems expose the same basic position lifecycle.

> **[Split](/trading/positions/manage.md#split-a-position)**
>
> Convert pUSD into a YES and NO token pair.

> **[Merge](/trading/positions/manage.md#merge-positions)**
>
> Convert a YES and NO token pair back into pUSD.

> **[Redeem](/trading/positions/manage.md#redeem-resolved-positions)**
>
> Exchange resolved outcome tokens for their payout.

## Token Flow

![pUSD can be split into YES and NO outcome tokens, which can be traded, merged, or redeemed after resolution.](https://mintcdn.com/polymarket-292d1b1b/FOMte3ewbG-LVy3k/images/core-concepts/token-flow.png?fit=max\&auto=format\&n=FOMte3ewbG-LVy3k\&q=85\&s=36f5a57946ac2b83136e17b6c06b358c)

## CTF

Conditional Tokens owns the outcome balances. Each outcome token has a unique
CTF position ID, used as its ERC-1155 token ID. CTF computes it onchain in three
steps.

**1. Compute the Condition ID**

```solidity theme={null}
getConditionId(oracle, questionId, outcomeSlotCount)
```

| Parameter | Type | Value |
| - | - | - |
| `oracle` | `address` | [UMA CTF Adapter](https://github.com/Polymarket/uma-ctf-adapter) |
| `questionId` | `bytes32` | Hash of the UMA ancillary data |
| `outcomeSlotCount` | `uint` | `2` for binary markets |

**2. Compute the Collection IDs**

```solidity theme={null}
getCollectionId(parentCollectionId, conditionId, indexSet)
```

| Parameter | Type | Value |
| - | - | - |
| `parentCollectionId` | `bytes32` | `bytes32(0)` for top-level positions |
| `conditionId` | `bytes32` | Condition ID from the previous step |
| `indexSet` | `uint` | `1` (`0b01`) for the first outcome or `2` (`0b10`) for the second |

The `indexSet` is a bitmask identifying which outcome slots belong to a
collection. It must be a nonempty proper subset of the condition's outcome
slots. A binary market has one collection for each outcome.

**3. Compute the Position IDs**

```solidity theme={null}
getPositionId(collateralToken, collectionId)
```

| Parameter | Type | Value |
| - | - | - |
| `collateralToken` | `IERC20` | pUSD contract address on Polygon |
| `collectionId` | `bytes32` | Collection ID for one market outcome |

The resulting position IDs are the ERC-1155 token IDs for the market's YES and
NO outcomes. Most integrations should read these token IDs from market data.
Computing them manually is only necessary for direct contract integrations.

| Market type | Position operations | Trading contract |
| - | - | - |
| Standard binary | `CtfCollateralAdapter` | CTF Exchange |
| Negative risk | `NegRiskCtfCollateralAdapter` | Negative Risk CTF Exchange |

For a binary condition, index set `1` identifies YES and index set `2`
identifies NO. Market data returns the resulting token IDs, so most integrations
do not need to derive them.

## Polymarket Protocol V2

PositionManager owns every outcome balance and routes each Position ID to its
registered module. The module ID is encoded in the Position ID.

| Module ID | Module | Position shape |
| - | - | - |
| `1` | BinaryModule | One YES and one NO position |
| `2` | NegRiskModule | One YES and one NO position for each event outcome |
| `3` | CombinatorialModule | One YES and one NO position for a set of legs |

### Position ID

A Position ID is the ERC-1155 token ID for a Polymarket Protocol V2 outcome.
Its 256 bits encode the module, condition, and outcome that define the
position. The first byte identifies the owning module, and the last byte
identifies the outcome, with `0` for YES and `1` for NO. Treat the complete
Position ID as an opaque decimal string in application code.

![The 256-bit position ID, split into moduleId, baseHash, arity, reserved, resolutionChain, conditionIndex, and outcomeIndex fields](https://mintcdn.com/polymarket-292d1b1b/_aswwXEhQSghbFEe/images/position-id-bit-layout.svg?fit=max\&auto=format\&n=_aswwXEhQSghbFEe\&q=85\&s=3f9b1b17df0c6e7de8157763a57886f4)

The Router is the public entry point for splits, merges, redemptions, and
module-specific position operations. A split requires pUSD approval for the
Router. A merge or redemption requires PositionManager approval for the Router.

## Negative Risk and Combos

Negative-risk events link several outcomes so a NO position can be converted
into the complementary YES positions. The two systems implement that behavior
through different contracts. See [Negative Risk
Markets](/concepts/negative-risk.md) for the event model.

Combos use PositionManager and the CombinatorialModule. Each leg is a
Polymarket Protocol V2 Position ID from the BinaryModule or NegRiskModule. The
resulting Combo is still a binary-shaped YES and NO pair, which allows it to use
Exchange v3.

See [Combinatorial Positions](/trading/positions/combinatorial.md) for the Combo
position model.

## Contract Addresses

See [Contracts](/resources/contracts.md) for Polymarket's current smart contract
addresses on Polygon.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.