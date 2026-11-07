> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Participation Protection

> Player props that resolve at their pregame price when the player sits out the second half.

> [!NOTE]
>
> Participation Protection is coming soon. Protected NFL markets open in QA first, starting Thursday, October 29, 2026. Ask your Novig contact for QA access.

A protected market is an NFL player prop with one extra rule.
If the player records no snap in the third quarter, fourth quarter, or overtime, the market resolves at its pregame fair market price.

It doesn't matter why the player sat out. An injury, an ejection, or a coach's decision all count.

A snap is any play the player is on the field for: any play from scrimmage, or any special-teams play.

## A protected market is a twin of a standard prop

We create each protected market next to a standard player prop.
The twin has the same event, the same player, and the same `strike`.

Its `marketType` is the standard type with `_PROTECTED` on the end.

| Standard market | Protected twin |
| - | - |
| `RECEIVING_YARDS` | `RECEIVING_YARDS_PROTECTED` |
| `RUSHING_YARDS` | `RUSHING_YARDS_PROTECTED` |

The two are separate markets, with their own IDs, order books, and prices.
An order on one never fills against the other.

## Which market types have a protected twin

Six NFL player prop types have a protected twin.

| Category | Protected types |
| - | - |
| Passing | `PASSING_YARDS_PROTECTED`, `PASSING_TOUCHDOWNS_PROTECTED` |
| Rushing | `RUSHING_YARDS_PROTECTED` |
| Receiving | `RECEIVING_YARDS_PROTECTED`, `RECEPTIONS_PROTECTED` |
| Touchdowns | `TOUCHDOWNS_PROTECTED` |

Every other market type stays standard.

## How a protected market settles

**1. The player records a snap after halftime**

The market grades like its standard twin.

**2. The player already reached the line**

The Over wins, even if the player then sits out.

**3. Neither is true**

The market resolves at its pregame fair market price.

A protected market can take longer to settle than its standard twin.

When a market resolves at its fair market price, each outcome's `status` is a bare decimal string, like `"0.550"`, not `WIN` or `LOSS`.
The two outcomes' prices sum to `1.000`, so an Over at `"0.550"` means the Under is at `"0.450"`.
Each contract pays its outcome's price × full value. See [Event lifecycle](https://docs.novig.com/api/concepts/event-lifecycle.md).

One snap in the third quarter is enough. A player who plays one snap and then leaves grades normally.

A wide receiver who passes 4.5 receptions in the first quarter and then leaves wins the Over.

A running back who leaves in the second quarter and never returns resolves at the pregame price. So does a player who is ejected in the first quarter.

### The contract states the rule

The contract wording repeats the protection rule, like this one for an Over:

> Resolves Yes if Patrick Mahomes records 300 or more passing yards in the Kansas City vs Tampa Bay NFL game currently scheduled for Sunday at 1:00 PM ET.
>
> Participation Protection
>
> * If Patrick Mahomes records no snap in the third quarter, fourth quarter or overtime, for any reason, the market resolves to its pregame fair market price, unless Patrick Mahomes has already recorded 300 or more passing yards.

The API doesn't return this text. An outcome's `name` is a display label, not the contract. Build on `marketType`.

## Find protected markets

Ask the catalog for a protected type:

```bash theme={"dark"}
curl -s "$NOVIG_HOST/v3/catalog/markets?league=NFL&marketType=RECEIVING_YARDS_PROTECTED" \
  -H "Novig-Key-Id: $TRADING_KEY_ID" \
  -H "Novig-Timestamp: $TS" \
  -H "Novig-Signature: $SIG"
```

`GET /v3/types/markets` lists every protected type once it is live.

A protected twin copies the consensus of its standard prop.
The protected type is its own market family, so `GET /v3/catalog/consensus` lists the twin's main line.

To find the twin of a standard prop, match on `eventId`, the player, `strike`, and `marketType`.
The twin's `marketType` is the standard type with `_PROTECTED` on the end.
Two prop types can share a player and a `strike`, so `marketType` is required.
`GET /v3/catalog/participants` lists the market IDs for each player. See [Participants](https://docs.novig.com/api/catalog#participants).

## Trade a protected market

You trade a protected market on its own order book, like any other market.
You can rest or take either side.

The Over buys protection. The Under sells it, so makers price that risk into the Under.

Trading closes at kickoff. A protected market has no live trading.

Until protected markets open in an environment, an order on one returns `MARKET_NOT_FOUND`.

## Protected legs in a parlay RFQ

A parlay can include protected legs. These rules apply on top of the [eligibility rules](https://docs.novig.com/api-reference/rfq/eligibility.md):

* Every protected leg is an Over pick.
* Every protected leg is on a game that hasn't started.
* Protection covers the whole parlay or none of it.
* A parlay can't hold a protected leg and the standard leg of the same player.

If one leg is protected, every leg that could be protected must be.
A leg could be protected when it is an Over pick, before the game, and its line has an open protected twin.

We refuse a parlay that breaks a rule, and no RFQ goes out. The requester sees one of these reasons:

| Reason | Cause |
| - | - |
| `Participation Protection isn't available right now.` | Protected parlays aren't open in this environment yet |
| `Participation Protection must cover every eligible player prop in the parlay.` | A leg that could be protected isn't |
| `This market isn't available for live parlays.` | A leg's game has started |
| `This side isn't available for parlays in this market.` | A protected leg is an Under pick |
| `Invalid parlay combination.` | The parlay holds a protected leg and the standard leg of the same player |

As a maker, you see a protected leg as an outcome on a protected market. Read its market's `marketType` to tell it apart from the standard leg.

A protected leg with no snap after halftime settles at its pregame fair market price. [Settlement](https://docs.novig.com/api-reference/rfq/settlement.md) says how such a leg pays out.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.