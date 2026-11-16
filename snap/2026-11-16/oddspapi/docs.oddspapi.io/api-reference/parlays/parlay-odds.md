> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Parlay Odds

> Price a parlay (accumulator) from selected `oddsIds`.

The legs are grouped by bookmaker and each group's prices are multiplied locally — no bookmaker round-trip and **no correlation adjustment**. Legs may span several fixtures and sports; for a correlated same-game price use `/fixtures/odds/sgp`.

Returns one normal fixture view per fixture a leg belongs to, ordered by `fixtureId`, each with a `parlay` section added. A cross-fixture parlay appears on every involved fixture with the same entry.

Max 20 legs per request.



## OpenAPI

````yaml /api-reference/openapi.json get /fixtures/odds/parlay
openapi: 3.1.0
info:
  title: Odds API v5
  version: 5.0.0
  description: High-performance odds API backed by Valkey + Supabase RPC discovery.
servers:
  - url: https://v5.oddspapi.io/en
security:
  - ApiKeyAuth: []
  - ApiKeyHeaderAuth: []
tags:
  - name: common
  - name: fixtures
  - name: fixtures/odds
  - name: parlays
  - name: futures
  - name: futures/odds
  - name: mapping
  - name: media
  - name: settlement
paths:
  /fixtures/odds/parlay:
    get:
      tags:
        - parlays
      summary: Parlay Odds
      description: >-
        Price a parlay (accumulator) from selected `oddsIds`.


        The legs are grouped by bookmaker and each group's prices are multiplied
        locally — no bookmaker round-trip and **no correlation adjustment**.
        Legs may span several fixtures and sports; for a correlated same-game
        price use `/fixtures/odds/sgp`.


        Returns one normal fixture view per fixture a leg belongs to, ordered by
        `fixtureId`, each with a `parlay` section added. A cross-fixture parlay
        appears on every involved fixture with the same entry.


        Max 20 legs per request.
      operationId: fixtures_odds_parlay__lang__fixtures_odds_parlay_get
      parameters:
        - $ref: '#/components/parameters/oddsIdsRequired'
      responses:
        '200':
          description: >-
            One FixtureParlayResponse per involved fixture, ordered by
            fixtureId.
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/FixtureParlayResponse'
              examples:
                priced:
                  summary: Two legs, one bookmaker, both live
                  description: >-
                    `price` is the product of 3.8 x 2.1. On this endpoint
                    `singlesPrice` always equals `price` while the parlay is
                    active.
                  value:
                    - fixtureId: id1400003171515924
                      reissuedFixtureId: null
                      previousFixtureId: null
                      status:
                        live: false
                        statusId: 0
                        statusName: Pre-Game
                      sport:
                        sportId: 14
                        sportName: American Football
                      tournament:
                        tournamentId: 31
                        tournamentName: NFL
                        categoryName: USA
                      season:
                        seasonId: 139396
                        seasonName: NFL 26/27
                        seasonRound: 6
                      venue:
                        venueId: 15672
                        venueName: Levi's Stadium
                        venueLocation: Santa Clara, CA, USA
                      startTime: 1792455300
                      participants:
                        participant1Id: 4389
                        participant1RotNr: 141
                        participant1Name: San Francisco 49ers
                        participant1ShortName: San Francisco
                        participant1Abbr: SF
                        participant2Id: 4432
                        participant2RotNr: 140
                        participant2Name: Washington Commanders
                        participant2ShortName: Washington
                        participant2Abbr: WAS
                      scores: {}
                      clock:
                        currentPeriod: null
                        currentTime: null
                        remainingTime: null
                        remainingTimeInPeriod: null
                        stopped: null
                        updatedAt: null
                      expectedPeriods: 4
                      periodLength: 15
                      externalProviders:
                        betgeniusId: null
                        betradarId: 71515924
                        pinnacleId: 1637707533
                        flashscoreId: Op9YIhXq
                        mollybetId: 2026-10-20,21640,10050390
                        oddinId: null
                        opticoddsId: 20261020BC02533C
                        sofascoreId: 16183736
                        lsportsId: 19776801
                        txoddsId: null
                      odds:
                        fanduel:
                          id1400003171515924:fanduel:141:0:
                            outcomeId: 141
                            playerId: 0
                            price: 1.24
                            active: true
                            changedAt: 1792442863661
                            bookmaker: fanduel
                            limit: 100000
                            marketId: 141
                            marketActive: true
                            mainLine: true
                            bookmakerMarketId: '734.190243703'
                            bookmakerOutcomeId: '43010249'
                            betslip: >-
                              https://sportsbook.fanduel.com/addToBetslip?marketId=734.190243703&selectionId=43010249
                            priceFractional: 6/25
                            priceAmerican: -417
                          id1400003171515924:fanduel:148:0:
                            outcomeId: 148
                            playerId: 0
                            price: 1.02
                            active: true
                            changedAt: 1792426006654
                            bookmaker: fanduel
                            limit: 100000
                            marketId: 148
                            marketActive: true
                            mainLine: false
                            bookmakerMarketId: '734.191320429'
                            bookmakerOutcomeId: '12194340'
                            betslip: >-
                              https://sportsbook.fanduel.com/addToBetslip?marketId=734.191320429&selectionId=12194340
                            priceFractional: 1/50
                            priceAmerican: -5000
                      bookmakers:
                        fanduel:
                          bookmaker: fanduel
                          bookmakerFixtureId: '36160515'
                          fixturePath: >-
                            https://sportsbook.fanduel.com/football/e/e-v-e-36160515
                          hasOdds: true
                          staleOdds: false
                          staleOddsResponseCode: null
                          suspended: false
                          participantsRotated: false
                          meta: null
                          updatedAt: '2026-10-18T09:51:14.662555+00:00'
                      parlay:
                        fanduel:
                          id1400003171515924:fanduel:141:0,id1400003171515924:fanduel:148:0:
                            bookmaker: fanduel
                            active: true
                            price: 1.265
                            changedAt: 1792444845041
                            priceFractional: 22/83
                            priceAmerican: -377
                            singlesPrice: 1.265
                      trueStartTime: null
                      trueEndTime: null
                compare_bookmakers:
                  summary: Same two legs at two bookmakers
                  description: >-
                    Request the same selections at several bookmakers and each
                    one is priced as its own parlay, side by side under its own
                    `parlayId`.
                  value:
                    - fixtureId: id1400003171515924
                      reissuedFixtureId: null
                      previousFixtureId: null
                      status:
                        live: false
                        statusId: 0
                        statusName: Pre-Game
                      sport:
                        sportId: 14
                        sportName: American Football
                      tournament:
                        tournamentId: 31
                        tournamentName: NFL
                        categoryName: USA
                      season:
                        seasonId: 139396
                        seasonName: NFL 26/27
                        seasonRound: 6
                      venue:
                        venueId: 15672
                        venueName: Levi's Stadium
                        venueLocation: Santa Clara, CA, USA
                      startTime: 1792455300
                      trueStartTime: null
                      trueEndTime: null
                      participants:
                        participant1Id: 4389
                        participant1RotNr: 141
                        participant1Name: San Francisco 49ers
                        participant1ShortName: San Francisco
                        participant1Abbr: SF
                        participant2Id: 4432
                        participant2RotNr: 140
                        participant2Name: Washington Commanders
                        participant2ShortName: Washington
                        participant2Abbr: WAS
                      scores: {}
                      clock:
                        currentPeriod: null
                        currentTime: null
                        remainingTime: null
                        remainingTimeInPeriod: null
                        stopped: null
                        updatedAt: null
                      expectedPeriods: 4
                      periodLength: 15
                      externalProviders:
                        betgeniusId: null
                        betradarId: 71515924
                        flashscoreId: Op9YIhXq
                        mollybetId: 2026-10-20,21640,10050390
                        oddinId: null
                        opticoddsId: 20261020BC02533C
                        pinnacleId: 1637707533
                        sofascoreId: 16183736
                        lsportsId: 19776801
                        txoddsId: null
                      odds:
                        bet365:
                          id1400003171515924:bet365:141:0:
                            outcomeId: 141
                            playerId: 0
                            price: 1.2
                            active: true
                            changedAt: 1792434136131
                            bookmaker: bet365
                            marketId: 141
                            mainLine: true
                            bookmakerMarketId: '1441'
                            bookmakerOutcomeId: '158515995'
                            priceFractional: 10/43
                            priceAmerican: -500
                          id1400003171515924:bet365:14256:0:
                            outcomeId: 14256
                            playerId: 0
                            price: 1.93
                            active: true
                            changedAt: 1792440107089
                            bookmaker: bet365
                            marketId: 14256
                            mainLine: true
                            bookmakerMarketId: '1441'
                            bookmakerOutcomeId: '158515989'
                            priceFractional: 25/26
                            priceAmerican: -108
                        pinnacle:
                          id1400003171515924:pinnacle:141:0:
                            outcomeId: 141
                            playerId: 0
                            price: 1.243
                            active: true
                            changedAt: 1792445529264
                            bookmaker: pinnacle
                            limit: 61728
                            marketId: 141
                            marketActive: true
                            mainLine: true
                            bookmakerMarketId: line/15/889/1637707533/3772276206/0/moneyline
                            bookmakerOutcomeId: home
                            bookmakerChangedAt: 1792445529003
                            priceFractional: 17/70
                            priceAmerican: -412
                          id1400003171515924:pinnacle:14256:0:
                            outcomeId: 14256
                            playerId: 0
                            price: 2.02
                            active: true
                            changedAt: 1792445529264
                            bookmaker: pinnacle
                            limit: 56250
                            marketId: 14256
                            marketActive: true
                            mainLine: true
                            bookmakerMarketId: line/15/889/1637707533/3772276206/0/spreads
                            bookmakerOutcomeId: '-7.5/home'
                            bookmakerChangedAt: 1792445529003
                            priceFractional: 51/50
                            priceAmerican: 102
                      bookmakers:
                        bet365:
                          bookmaker: bet365
                          bookmakerFixtureId: '202477548'
                          fixturePath: >-
                            https://www.bet365.com/#/AC/B12/C20426855/D19/E26920350/F19/I0/
                          hasOdds: true
                          staleOdds: false
                          staleOddsResponseCode: 0
                          suspended: false
                          participantsRotated: false
                          meta: null
                          updatedAt: '2026-10-19T19:33:53.008044+00:00'
                        pinnacle:
                          bookmaker: pinnacle
                          bookmakerFixtureId: '1637707533'
                          fixturePath: https://www.pinnacle.com/en/e/e/e/1637707533
                          hasOdds: true
                          staleOdds: false
                          staleOddsResponseCode: 502
                          suspended: false
                          participantsRotated: false
                          meta: null
                          updatedAt: '2026-10-17T14:00:01.921523+00:00'
                      parlay:
                        bet365:
                          id1400003171515924:bet365:141:0,id1400003171515924:bet365:14256:0:
                            bookmaker: bet365
                            active: true
                            price: 2.316
                            changedAt: 1792445595800
                            priceFractional: 25/19
                            priceAmerican: 132
                            singlesPrice: 2.316
                        pinnacle:
                          id1400003171515924:pinnacle:141:0,id1400003171515924:pinnacle:14256:0:
                            bookmaker: pinnacle
                            active: true
                            price: 2.511
                            changedAt: 1792445595800
                            priceFractional: 68/45
                            priceAmerican: 151
                            singlesPrice: 2.511
                multi_fixture:
                  summary: Legs across two fixtures (accumulator)
                  description: >-
                    The parlay spans an NFL and an NBA fixture. It is returned
                    on BOTH fixtures with the same `parlayId` and the same
                    entry; each fixture's `odds` section holds only its own leg.
                  value:
                    - fixtureId: id1100013273682356
                      reissuedFixtureId: null
                      previousFixtureId: null
                      status:
                        live: false
                        statusId: 0
                        statusName: Pre-Game
                      sport:
                        sportId: 11
                        sportName: Basketball
                      tournament:
                        tournamentId: 132
                        tournamentName: NBA
                        categoryName: USA
                      season:
                        seasonId: 142074
                        seasonName: NBA 26/27
                        seasonRound: 1
                      venue:
                        venueId: 6054
                        venueName: Madison Square Garden
                        venueLocation: New York, NY, USA
                      startTime: 1792537200
                      participants:
                        participant1Id: 3421
                        participant1RotNr: 504
                        participant1Name: New York Knicks
                        participant1ShortName: New York
                        participant1Abbr: NYK
                        participant2Id: 3420
                        participant2RotNr: 503
                        participant2Name: Philadelphia 76ers
                        participant2ShortName: Philadelphia
                        participant2Abbr: PHI
                      scores: {}
                      clock:
                        currentPeriod: null
                        currentTime: null
                        remainingTime: null
                        remainingTimeInPeriod: null
                        stopped: null
                        updatedAt: null
                      expectedPeriods: 4
                      periodLength: 12
                      externalProviders:
                        betgeniusId: 14304079
                        betradarId: 73682356
                        pinnacleId: 1633801639
                        flashscoreId: prbYk4Kq
                        mollybetId: 2026-10-20,29093,29094
                        oddinId: null
                        opticoddsId: 20261020D42F1162
                        sofascoreId: 16814811
                        lsportsId: 19766754
                        txoddsId: null
                      odds:
                        bet365:
                          id1100013273682356:bet365:111114:0:
                            outcomeId: 111114
                            playerId: 0
                            price: 1.84
                            active: true
                            changedAt: 1792440103347
                            bookmaker: bet365
                            marketId: 111114
                            mainLine: true
                            bookmakerMarketId: '928'
                            bookmakerOutcomeId: '257214164'
                            priceFractional: 50/57
                            priceAmerican: -119
                      bookmakers:
                        bet365:
                          bookmaker: bet365
                          bookmakerFixtureId: '199419308'
                          fixturePath: >-
                            https://www.bet365.com/#/AC/B18/C20604387/D19/E26451062/F19/I0/
                          hasOdds: true
                          staleOdds: false
                          suspended: false
                          participantsRotated: false
                          updatedAt: '2026-10-19T19:33:48.800972+00:00'
                          staleOddsResponseCode: 0
                          meta: null
                      parlay:
                        bet365:
                          id1100013273682356:bet365:111114:0,id1400003171515924:bet365:141161:0:
                            bookmaker: bet365
                            active: true
                            price: 4.14
                            changedAt: 1792444801056
                            priceFractional: 157/50
                            priceAmerican: 314
                            singlesPrice: 4.14
                      trueStartTime: null
                      trueEndTime: null
                    - fixtureId: id1400003171515924
                      reissuedFixtureId: null
                      previousFixtureId: null
                      status:
                        live: false
                        statusId: 0
                        statusName: Pre-Game
                      sport:
                        sportId: 14
                        sportName: American Football
                      tournament:
                        tournamentId: 31
                        tournamentName: NFL
                        categoryName: USA
                      season:
                        seasonId: 139396
                        seasonName: NFL 26/27
                        seasonRound: 6
                      venue:
                        venueId: 15672
                        venueName: Levi's Stadium
                        venueLocation: Santa Clara, CA, USA
                      startTime: 1792455300
                      participants:
                        participant1Id: 4389
                        participant1RotNr: 141
                        participant1Name: San Francisco 49ers
                        participant1ShortName: San Francisco
                        participant1Abbr: SF
                        participant2Id: 4432
                        participant2RotNr: 140
                        participant2Name: Washington Commanders
                        participant2ShortName: Washington
                        participant2Abbr: WAS
                      scores: {}
                      clock:
                        currentPeriod: null
                        currentTime: null
                        remainingTime: null
                        remainingTimeInPeriod: null
                        stopped: null
                        updatedAt: null
                      expectedPeriods: 4
                      periodLength: 15
                      externalProviders:
                        betgeniusId: null
                        betradarId: 71515924
                        pinnacleId: 1637707533
                        flashscoreId: Op9YIhXq
                        mollybetId: 2026-10-20,21640,10050390
                        oddinId: null
                        opticoddsId: 20261020BC02533C
                        sofascoreId: 16183736
                        lsportsId: 19776801
                        txoddsId: null
                      odds:
                        bet365:
                          id1400003171515924:bet365:141161:0:
                            outcomeId: 141161
                            playerId: 0
                            price: 2.25
                            active: true
                            changedAt: 1792443907432
                            bookmaker: bet365
                            marketId: 141161
                            mainLine: true
                            bookmakerMarketId: '121207'
                            bookmakerOutcomeId: '200618997'
                            priceFractional: 5/4
                            priceAmerican: 125
                      bookmakers:
                        bet365:
                          bookmaker: bet365
                          bookmakerFixtureId: '202477548'
                          fixturePath: >-
                            https://www.bet365.com/#/AC/B12/C20426855/D19/E26920350/F19/I0/
                          hasOdds: true
                          staleOdds: false
                          suspended: false
                          participantsRotated: false
                          updatedAt: '2026-10-19T19:33:53.008044+00:00'
                          staleOddsResponseCode: 0
                          meta: null
                      parlay:
                        bet365:
                          id1100013273682356:bet365:111114:0,id1400003171515924:bet365:141161:0:
                            bookmaker: bet365
                            active: true
                            price: 4.14
                            changedAt: 1792444801056
                            priceFractional: 157/50
                            priceAmerican: 314
                            singlesPrice: 4.14
                      trueStartTime: null
                      trueEndTime: null
                leg_suspended:
                  summary: A leg exists but is currently OFF
                  description: >-
                    The leg is still returned in `odds` with `active: false`,
                    and the parlay keeps an entry: inactive, `price: 1` (stake
                    back), `meta.inactiveReason` naming the cause, and
                    `singlesPrice` still showing what it would have paid.
                  value:
                    - fixtureId: id1400003171515924
                      reissuedFixtureId: null
                      previousFixtureId: null
                      status:
                        live: false
                        statusId: 0
                        statusName: Pre-Game
                      sport:
                        sportId: 14
                        sportName: American Football
                      tournament:
                        tournamentId: 31
                        tournamentName: NFL
                        categoryName: USA
                      season:
                        seasonId: 139396
                        seasonName: NFL 26/27
                        seasonRound: 6
                      venue:
                        venueId: 15672
                        venueName: Levi's Stadium
                        venueLocation: Santa Clara, CA, USA
                      startTime: 1792455300
                      trueStartTime: null
                      trueEndTime: null
                      participants:
                        participant1Id: 4389
                        participant1RotNr: 141
                        participant1Name: San Francisco 49ers
                        participant1ShortName: San Francisco
                        participant1Abbr: SF
                        participant2Id: 4432
                        participant2RotNr: 140
                        participant2Name: Washington Commanders
                        participant2ShortName: Washington
                        participant2Abbr: WAS
                      scores: {}
                      clock:
                        currentPeriod: null
                        currentTime: null
                        remainingTime: null
                        remainingTimeInPeriod: null
                        stopped: null
                        updatedAt: null
                      expectedPeriods: 4
                      periodLength: 15
                      externalProviders:
                        betgeniusId: null
                        betradarId: 71515924
                        flashscoreId: Op9YIhXq
                        mollybetId: 2026-10-20,21640,10050390
                        oddinId: null
                        opticoddsId: 20261020BC02533C
                        pinnacleId: 1637707533
                        sofascoreId: 16183736
                        lsportsId: 19776801
                        txoddsId: null
                      odds:
                        bet365:
                          id1400003171515924:bet365:141:0:
                            outcomeId: 141
                            playerId: 0
                            price: 1.2
                            active: true
                            changedAt: 1792434136131
                            bookmaker: bet365
                            marketId: 141
                            mainLine: true
                            bookmakerMarketId: '1441'
                            bookmakerOutcomeId: '158515995'
                            priceFractional: 10/43
                            priceAmerican: -500
                          id1400003171515924:bet365:14256:0:
                            outcomeId: 14256
                            playerId: 0
                            price: 1.93
                            active: false
                            changedAt: 1792440107089
                            bookmaker: bet365
                            marketId: 14256
                            mainLine: true
                            bookmakerMarketId: '1441'
                            bookmakerOutcomeId: '158515989'
                            priceFractional: 25/26
                            priceAmerican: -108
                      bookmakers:
                        bet365:
                          bookmaker: bet365
                          bookmakerFixtureId: '202477548'
                          fixturePath: >-
                            https://www.bet365.com/#/AC/B12/C20426855/D19/E26920350/F19/I0/
                          hasOdds: true
                          staleOdds: false
                          staleOddsResponseCode: 0
                          suspended: false
                          participantsRotated: false
                          meta: null
                          updatedAt: '2026-10-19T19:33:53.008044+00:00'
                      parlay:
                        bet365:
                          id1400003171515924:bet365:141:0,id1400003171515924:bet365:14256:0:
                            bookmaker: bet365
                            active: false
                            price: 1
                            changedAt: 1792445595800
                            priceFractional: 0/1
                            priceAmerican: 0
                            singlesPrice: 2.316
                leg_missing:
                  summary: A leg could not be found
                  description: >-
                    Unknown bookmaker, or an unknown/expired oddsId. Pricing
                    half a parlay would be meaningless, so the bookmaker gets NO
                    entry and `parlay` comes back `{}` — `missingOddsIds` names
                    the legs that were not found.
                  value:
                    - fixtureId: id1400003171515924
                      reissuedFixtureId: null
                      previousFixtureId: null
                      status:
                        live: false
                        statusId: 0
                        statusName: Pre-Game
                      sport:
                        sportId: 14
                        sportName: American Football
                      tournament:
                        tournamentId: 31
                        tournamentName: NFL
                        categoryName: USA
                      season:
                        seasonId: 139396
                        seasonName: NFL 26/27
                        seasonRound: 6
                      venue:
                        venueId: 15672
                        venueName: Levi's Stadium
                        venueLocation: Santa Clara, CA, USA
                      startTime: 1792455300
                      participants:
                        participant1Id: 4389
                        participant1RotNr: 141
                        participant1Name: San Francisco 49ers
                        participant1ShortName: San Francisco
                        participant1Abbr: SF
                        participant2Id: 4432
                        participant2RotNr: 140
                        participant2Name: Washington Commanders
                        participant2ShortName: Washington
                        participant2Abbr: WAS
                      scores: {}
                      clock:
                        currentPeriod: null
                        currentTime: null
                        remainingTime: null
                        remainingTimeInPeriod: null
                        stopped: null
                        updatedAt: null
                      expectedPeriods: 4
                      periodLength: 15
                      externalProviders:
                        betgeniusId: null
                        betradarId: 71515924
                        pinnacleId: 1637707533
                        flashscoreId: Op9YIhXq
                        mollybetId: 2026-10-20,21640,10050390
                        oddinId: null
                        opticoddsId: 20261020BC02533C
                        sofascoreId: 16183736
                        lsportsId: 19776801
                        txoddsId: null
                      odds:
                        bet365:
                          id1400003171515924:bet365:141161:0:
                            outcomeId: 141161
                            playerId: 0
                            price: 2.25
                            active: true
                            changedAt: 1792443907432
                            bookmaker: bet365
                            marketId: 141161
                            mainLine: true
                            bookmakerMarketId: '121207'
                            bookmakerOutcomeId: '200618997'
                            priceFractional: 5/4
                            priceAmerican: 125
                      bookmakers:
                        bet365:
                          bookmaker: bet365
                          bookmakerFixtureId: '202477548'
                          fixturePath: >-
                            https://www.bet365.com/#/AC/B12/C20426855/D19/E26920350/F19/I0/
                          hasOdds: true
                          staleOdds: false
                          suspended: false
                          participantsRotated: false
                          updatedAt: '2026-10-19T19:33:53.008044+00:00'
                          staleOddsResponseCode: 0
                          meta: null
                      missingOddsIds:
                        - id1400003171515924:bet365:999999:0
                      parlay: {}
                      trueStartTime: null
                      trueEndTime: null
        '400':
          $ref: '#/components/responses/ApiError'
        '401':
          $ref: '#/components/responses/ApiError'
        '403':
          $ref: '#/components/responses/ApiError'
        '422':
          $ref: '#/components/responses/ValidationError'
        '429':
          $ref: '#/components/responses/ApiError'
components:
  parameters:
    oddsIdsRequired:
      name: oddsIds
      in: query
      required: true
      schema:
        type: string
      description: >-
        Comma- or space-separated oddsIds in the form
        `{fixtureId}:{bookmaker}:{outcomeId}:{playerId}`. Duplicates are removed
        and the list is sorted. Each bookmaker's legs form one parlay. Max 20
        legs.
  schemas:
    FixtureParlayResponse:
      allOf:
        - $ref: '#/components/schemas/Fixture'
        - type: object
          properties:
            odds:
              allOf:
                - $ref: '#/components/schemas/OddsByBookmaker'
              description: >-
                The requested legs of this fixture and nothing else — inactive
                ones included (no `activeOnly` filtering). Identical in shape
                and content to what `/fixtures/odds` returns for those rows.
            bookmakers:
              type: object
              additionalProperties:
                $ref: '#/components/schemas/BookmakerFixtureMeta'
              description: >-
                bookmakerSlug -> BookmakerFixtureMeta, for the requested
                bookmakers only.
            missingOddsIds:
              type: array
              items:
                type: string
              description: >-
                Requested oddsIds of this fixture that could not be found
                (unknown bookmaker, unknown or expired oddsId). Present only
                when at least one leg is missing.
            parlay:
              $ref: '#/components/schemas/ParlayByBookmaker'
          required:
            - odds
            - bookmakers
            - parlay
          additionalProperties: true
    Fixture:
      type: object
      properties:
        fixtureId:
          type: string
          description: >-
            Fixture identifier: `{prefix}{sportId}{tournamentId}{nativeId}` —
            see Core Concepts → ID structure.
        reissuedFixtureId:
          type:
            - string
            - 'null'
          description: >-
            The fixture that replaced this one, when a cancelled fixture has
            been re-listed under a new id. `null` is the normal value: it is
            only ever populated on a cancelled fixture (`status.statusId` `3`)
            whose replacement already exists in the feed, so permanent
            cancellations stay `null`, and a fixture can go from `null` to an id
            after you first saw it cancelled. One hop: if the replacement was
            itself cancelled, follow its own `reissuedFixtureId`.
        previousFixtureId:
          type:
            - string
            - 'null'
          description: >-
            The inverse of `reissuedFixtureId`: the cancelled fixture this one
            replaced. Unlike the forward pointer this sits on a normal scheduled
            or live fixture, so a fixture carries its own history even if you
            never saw the cancelled one. `null` is the normal value. One hop and
            one predecessor at most — follow it repeatedly to walk back to the
            start of a chain, where a fixture postponed twice gives `A → B → C`
            and `B` carries both `previousFixtureId: A` and `reissuedFixtureId:
            C`. Cleared again if a postponement is reverted.
        status:
          $ref: '#/components/schemas/Status'
          description: Lifecycle status of the fixture.
        sport:
          $ref: '#/components/schemas/SportRef'
          description: Sport reference.
        tournament:
          $ref: '#/components/schemas/TournamentRef'
          description: Tournament reference.
        season:
          $ref: '#/components/schemas/SeasonRef'
          description: Season reference, when resolved.
        venue:
          $ref: '#/components/schemas/VenueRef'
          description: Venue reference, when known.
        startTime:
          type: integer
          description: Epoch seconds (UTC).
        trueStartTime:
          type:
            - string
            - 'null'
          description: >-
            Actual start time (ISO 8601), once known — may differ from the
            scheduled `startTime`.
        trueEndTime:
          type:
            - string
            - 'null'
          description: Actual end time (ISO 8601), once known.
        participants:
          $ref: '#/components/schemas/ParticipantsRef'
          description: Both participants with names, rotation numbers and abbreviations.
        scores:
          type: object
          additionalProperties:
            $ref: '#/components/schemas/ScorePeriod'
          description: Score rows keyed by period (`result`, `p1`, …).
        clock:
          anyOf:
            - $ref: '#/components/schemas/Clock'
            - type: 'null'
          description: Live match clock, while in play.
        expectedPeriods:
          type:
            - integer
            - 'null'
          description: >-
            Number of regular periods expected for this fixture — use it (with
            `periodLength`) to interpret period keys per sport/league.
        periodLength:
          type:
            - integer
            - 'null'
          description: Length of a regular period in minutes.
        externalProviders:
          $ref: '#/components/schemas/ExternalProviders'
          description: >-
            Native identifiers of this fixture at external providers (betradar,
            pinnacle, …).
        bookmakers:
          type: object
          additionalProperties:
            $ref: '#/components/schemas/BookmakerFixtureMeta'
          description: >-
            Per-bookmaker availability and freshness metadata. Only populated
            when the `bookmakers` query parameter is present on the request —
            otherwise `{}`. Contains only bookmakers that currently offer valid
            realtime odds for this fixture. Present on odds endpoints; omitted
            on CLV, historical-odds and settlement responses.
      required:
        - fixtureId
        - reissuedFixtureId
        - previousFixtureId
        - status
        - sport
        - tournament
        - season
        - venue
        - startTime
        - participants
        - scores
        - clock
        - externalProviders
      additionalProperties: true
    OddsByBookmaker:
      type: object
      additionalProperties:
        $ref: '#/components/schemas/OddsMap'
    BookmakerFixtureMeta:
      type: object
      properties:
        bookmaker:
          type: string
          description: Bookmaker slug.
        bookmakerFixtureId:
          type:
            - string
            - 'null'
          description: Native fixture identifier at the bookmaker.
        fixturePath:
          type:
            - string
            - 'null'
          description: Path of the fixture page on the bookmaker's site, when known.
        hasOdds:
          type: boolean
          description: True if the bookmaker currently publishes odds for this fixture.
        staleOdds:
          type: boolean
          description: >-
            Critical for trading: true if the connection to this bookmaker was
            lost or interrupted, so odds freshness can no longer be guaranteed.
        staleOddsResponseCode:
          type:
            - integer
            - 'null'
          deprecated: true
          description: >-
            **Deprecated.** HTTP status code observed during the staleness
            check, when any. Rely on `staleOdds` instead.
        suspended:
          type: boolean
          description: True while the bookmaker has suspended betting on this fixture.
        participantsRotated:
          type: boolean
          description: >-
            True if the bookmaker lists the participants in the opposite order
            (participant 1/2 swapped) relative to OddsPapi.
        meta:
          type:
            - object
            - 'null'
          additionalProperties: true
          description: Bookmaker-specific metadata.
        updatedAt:
          type: string
          description: When this bookmaker meta last changed.
      required:
        - bookmaker
        - hasOdds
        - staleOdds
        - suspended
        - participantsRotated
        - updatedAt
      additionalProperties: true
    ParlayByBookmaker:
      type: object
      description: >-
        bookmakerSlug -> (parlayId -> ParlayEntry). `parlayId` is the sorted,
        deduped oddsIds joined by `,`. A bookmaker whose legs could not all be
        found is omitted entirely, so this object can be `{}`.
      additionalProperties:
        type: object
        additionalProperties:
          $ref: '#/components/schemas/ParlayEntry'
    Error:
      type: object
      description: Concrete error format returned by this API (no FastAPI detail wrapper).
      properties:
        error:
          type: integer
          description: HTTP status code
        message:
          type: string
          description: Human-readable error message
        details:
          description: Optional extra details (string/object/array depending on the error).
        reason:
          type: string
          deprecated: true
          description: '**Deprecated** alias of `code`. Read `code`.'
        code:
          type: string
          description: Machine-readable reason code.
      required:
        - error
        - message
      additionalProperties: true
    HTTPValidationError:
      title: HTTPValidationError
      type: object
      properties:
        detail:
          title: Detail
          type: array
          items:
            $ref: '#/components/schemas/ValidationError'
          description: List of validation errors.
    Status:
      type: object
      properties:
        live:
          type: boolean
          description: Convenience flag — true only while in play (`statusId` 1).
        statusId:
          type: integer
          description: >-
            Lifecycle status: `0` pregame, `1` live, `2` finished, `3`
            cancelled. Moves forward only — always branch on this ID.
        statusName:
          type: string
          description: >-
            Translated display label for the status. For logic, branch on
            `statusId`, never on this string.
      required:
        - live
        - statusId
        - statusName
      additionalProperties: true
    SportRef:
      type: object
      properties:
        sportId:
          type: integer
          description: Numeric sport identifier.
        sportName:
          type: string
          description: Sport display name, translated per the request language prefix.
      required:
        - sportId
        - sportName
      additionalProperties: true
    TournamentRef:
      type: object
      properties:
        tournamentId:
          type: integer
          description: Numeric tournament identifier.
        tournamentName:
          type: string
          description: Tournament display name, translated per the request language prefix.
        categoryName:
          type:
            - string
            - 'null'
          description: Category (usually country/region) display name, translated.
      required:
        - tournamentId
        - tournamentName
      additionalProperties: true
    SeasonRef:
      type: object
      properties:
        seasonId:
          type:
            - integer
            - 'null'
          description: Numeric season identifier, when known.
        seasonName:
          type:
            - string
            - 'null'
          description: Season display name, translated.
        seasonRound:
          type:
            - integer
            - 'null'
          description: Round number within the season, when applicable.
      additionalProperties: true
    VenueRef:
      type: object
      properties:
        venueId:
          type:
            - integer
            - 'null'
          description: Numeric venue identifier, when known.
        venueName:
          type:
            - string
            - 'null'
          description: Venue display name, translated.
        venueLocation:
          type:
            - string
            - 'null'
          description: Venue location (city), translated.
      additionalProperties: true
    ParticipantsRef:
      type: object
      properties:
        participant1Id:
          type: integer
          description: Identifier of participant 1 (home / first-listed side).
        participant1RotNr:
          type:
            - integer
            - 'null'
          description: >-
            Rotation number of participant 1 (US-book convention), when
            available.
        participant1Name:
          type:
            - string
            - 'null'
          description: Full name of participant 1, translated.
        participant1ShortName:
          type:
            - string
            - 'null'
          description: Short name of participant 1.
        participant1Abbr:
          type:
            - string
            - 'null'
          description: Abbreviation of participant 1.
        participant2Id:
          type: integer
          description: Identifier of participant 2 (away / second-listed side).
        participant2RotNr:
          type:
            - integer
            - 'null'
          description: >-
            Rotation number of participant 2 (US-book convention), when
            available.
        participant2Name:
          type:
            - string
            - 'null'
          description: Full name of participant 2, translated.
        participant2ShortName:
          type:
            - string
            - 'null'
          description: Short name of participant 2.
        participant2Abbr:
          type:
            - string
            - 'null'
          description: Abbreviation of participant 2.
      required:
        - participant1Id
        - participant2Id
      additionalProperties: true
    ScorePeriod:
      type: object
      properties:
        period:
          type: string
          description: >-
            Period key this score row refers to (`result` = overall scoreline;
            see Enumerations).
        participant1Score:
          type: integer
          description: Score of participant 1 for this period.
        participant2Score:
          type: integer
          description: Score of participant 2 for this period.
        updatedAt:
          type: string
          description: When this score value last changed.
      required:
        - period
        - participant1Score
        - participant2Score
        - updatedAt
      additionalProperties: true
    Clock:
      type: object
      properties:
        currentPeriod:
          type:
            - string
            - 'null'
          description: >-
            Period key currently being played (see the period vocabulary in
            Enumerations).
        currentTime:
          type:
            - string
            - 'null'
          description: Elapsed match time, `mm:ss`.
        remainingTime:
          type:
            - string
            - 'null'
          description: Remaining match time, `mm:ss` (count-down sports).
        remainingTimeInPeriod:
          type:
            - string
            - 'null'
          description: Remaining time in the current period, `mm:ss`.
        stopped:
          type:
            - boolean
            - 'null'
          description: True while the match clock is stopped.
      additionalProperties: true
    ExternalProviders:
      type: object
      additionalProperties: true
    OddsMap:
      type: object
      additionalProperties:
        $ref: '#/components/schemas/OddQuote'
    ParlayEntry:
      type: object
      description: >-
        One bookmaker's price for one parlay. Produced locally by multiplying
        the leg prices — no correlation adjustment.
      properties:
        bookmaker:
          type: string
          description: Bookmaker slug.
        active:
          type: boolean
          description: >-
            False when a leg is suspended, its market is closed, or the
            bookmaker is suspended for an involved fixture.
        price:
          type: number
          description: >-
            Decimal product of the leg prices, 3 dp. `1` when the parlay is not
            bettable (stake back).
        priceFractional:
          type: string
          description: Derived from `price`; present while `price > 1`.
        priceAmerican:
          type: integer
          description: Derived from `price`; present while `price > 1`.
        singlesPrice:
          type: number
          description: >-
            The naive product of the legs. Equals `price` on
            `/fixtures/odds/parlay`; on `/fixtures/odds/sgp` the gap between the
            two is the correlation margin.
        changedAt:
          type: integer
          description: When this entry was priced, UTC epoch milliseconds.
        meta:
          type: object
          description: Present when there is something to report.
          properties:
            inactiveReason:
              type: string
              enum:
                - leg_suspended
                - market_closed
                - bookmaker_suspended
              description: Why `active` is false.
          additionalProperties: true
      required:
        - bookmaker
        - active
        - price
        - singlesPrice
        - changedAt
      additionalProperties: true
    ValidationError:
      title: ValidationError
      type: object
      required:
        - loc
        - msg
        - type
      properties:
        loc:
          title: Location
          type: array
          items:
            anyOf:
              - type: string
              - type: integer
          description: Location of the invalid parameter (path segments).
        msg:
          title: Message
          type: string
          description: Human-readable validation message.
        type:
          title: Error Type
          type: string
          description: Machine-readable error type.
    OddQuote:
      type: object
      properties:
        bookmaker:
          type: string
          description: Bookmaker slug.
        outcomeId:
          type: integer
          description: Frozen outcome identifier of the selection.
        playerId:
          type: integer
          description: Player the price refers to; `0` for non-player markets.
        price:
          type: number
          description: Decimal odds.
        active:
          type: boolean
          description: Whether this outcome is currently available at the bookmaker.
        marketActive:
          type:
            - boolean
            - 'null'
          description: >-
            Whether the entire market is active at the bookmaker. Advisory and
            optional — many bookmakers never report it, so `null` is common and
            does not mean the market is closed (as one example, every quote from
            `bet365` on a sampled fixture returned `null` while `pinnacle`
            returned `true` on all of its own). Use `active` on the quote itself
            as the authoritative per-row state; treat `marketActive` as extra
            context only.
        mainLine:
          type:
            - boolean
            - 'null'
          description: Whether this is the bookmaker's main line for the market type.
        marketId:
          type: integer
          description: Frozen market identifier the outcome belongs to.
        bookmakerMarketId:
          type:
            - string
            - 'null'
          description: Native bookmaker market identifier.
        bookmakerOutcomeId:
          type:
            - string
            - 'null'
          description: Native bookmaker outcome identifier.
        bookmakerChangedAt:
          type:
            - integer
            - 'null'
          description: Bookmaker-provided change timestamp (epoch ms), when present.
        priceFractional:
          type:
            - string
            - 'null'
          description: Fractional odds (e.g. `5/2`).
        priceAmerican:
          type:
            - integer
            - 'null'
          description: American odds (e.g. `-110`, `+250`).
        meta:
          type:
            - object
            - 'null'
          description: Bookmaker-specific metadata (orderbooks, ladders, …).
        limit:
          type:
            - number
            - 'null'
          description: Maximum accepted stake, when provided by the bookmaker.
        betslip:
          type:
            - string
            - 'null'
          description: Bookmaker betslip / deeplink token, when available.
        changedAt:
          type: integer
          description: >-
            When OddsPapi observed this change at the bookmaker (epoch ms, UTC)
            — always present.
      required:
        - bookmaker
        - outcomeId
        - playerId
        - price
        - active
        - changedAt
      additionalProperties: true
  responses:
    ApiError:
      description: API error
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/Error'
          examples:
            invalid_filters:
              summary: Invalid filters
              value:
                error: 400
                message: Invalid filters.
                reason: invalid_filters
            invalid_api_key:
              summary: Invalid apiKey
              value:
                error: 401
                message: invalid apiKey
                reason: invalid_api_key
            rate_limited:
              summary: Rate limited
              value:
                error: 429
                message: rate limit exceeded
                reason: rate_limited
                retryAfterSec: 1
    ValidationError:
      description: Validation Error (framework-level)
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/HTTPValidationError'
  securitySchemes:
    ApiKeyAuth:
      type: apiKey
      in: query
      name: apiKey
      description: Your API key as the `apiKey` query parameter.
    ApiKeyHeaderAuth:
      type: apiKey
      in: header
      name: X-API-Key
      description: Your API key as the `X-API-Key` request header.

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.