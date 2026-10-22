> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Betting delays

> Betting delays added to guard against toxic flow.

Read `data.bettingDelay` from [`GET /metadata/obv3`](/api-reference/get-metadata-obv3). Every value is milliseconds.

A delay applies only to an order that takes resting liquidity. An order that only adds liquidity is
accepted immediately and goes `ACTIVE` without waiting.

A delayed order matches against the book as it stands when the delay elapses, not as it stood when
you submitted it, so it can fill less than the depth you saw, or nothing at all.

## Live

Resolve in this order: `leagueIdToMs[leagueId]`, then `sportIdToMs[sportId]`, then `liveMsDefault`.

Live means the market's `gameTime` has passed, not that the fixture is in play. A late-kickoff market still counts as live once `gameTime` has elapsed.

## Pregame

Use `pregameMsDefault`. There is no league or sport override.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.