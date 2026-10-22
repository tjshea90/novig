> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Same-Game Parlay Odds

> Price a correlated same-game parlay (SGP) from selected `oddsIds`.

All `oddsIds` must belong to **one** fixture. The legs are priced by the bookmaker itself, so `price` reflects the correlation between them; `singlesPrice` is the naive product and the gap is the correlation margin. Several bookmakers may be requested at once and are returned side by side.

Returns ONE fixture view with an `sgp` section added.

Max 20 legs per request.



## OpenAPI

````yaml /api-reference/openapi.json get /fixtures/odds/sgp
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
  /fixtures/odds/sgp:
    get:
      tags:
        - parlays
      summary: Same-Game Parlay Odds
      description: >-
        Price a correlated same-game parlay (SGP) from selected `oddsIds`.


        All `oddsIds` must belong to **one** fixture. The legs are priced by the
        bookmaker itself, so `price` reflects the correlation between them;
        `singlesPrice` is the naive product and the gap is the correlation
        margin. Several bookmakers may be requested at once and are returned
        side by side.


        Returns ONE fixture view with an `sgp` section added.


        Max 20 legs per request.
      operationId: fixtures_odds_sgp__lang__fixtures_odds_sgp_get
      parameters:
        - name: oddsIds
          in: query
          required: true
          schema:
            type: string
          description: >-
            Comma- or space-separated oddsIds in the form
            `{fixtureId}:{bookmaker}:{outcomeId}:{playerId}`. Duplicates are
            removed and the list is sorted. Each bookmaker's legs form one
            parlay. Max 20 legs. All must share one fixture.
      responses:
        '200':
          description: One FixtureSgpResponse for the fixture the legs belong to.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/FixtureSgpResponse'
              examples:
                priced:
                  summary: Two correlated legs, quoted by the bookmaker
                  description: >-
                    `singlesPrice` 7.98 is what the naive product would pay; the
                    bookmaker's correlated `price` is 6.5. `limit` is the max
                    stake and `betslip` opens the wager pre-filled.
                  value:
                    fixtureId: id1400003171515924
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
                    sgp:
                      fanduel:
                        id1400003171515924:fanduel:141:0,id1400003171515924:fanduel:148:0:
                          bookmaker: fanduel
                          active: true
                          price: 1.252
                          changedAt: 1792444844721
                          priceFractional: 25/99
                          priceAmerican: -397
                          limit: 100000
                          singlesPrice: 1.265
                          bookmakerParlayId: >-
                            2yJJprlTc/iJ6lerM79jtmjxI65FLEFWRq91Z2m2FvcfKwyx/uUzC1BGyJyb8R9/ZJmuGWvdWLn6q5WQbRMXe1JqXFCdnOQWvJxe3BId/s2Q/jmFP0E6We8OmU5RMo2/LcLbfOumsL1K36cwZnhE8EDalrkxeFIa2yLHm5cz2kMQW+zN28/ttDVygWqP9/bgXHEZibVuSMM1yEhOKJX70v5LKIZAJNr4UUugLhuLGGzwMzFbo4TaaInROwM+S+FuGRmSno6DNrs5jMvk4RV2Egy33FbTF2IKOlkASrBNon5vygquCf24821eaGm56WQcMMAUqtKiQ7ctvdGWbiGaZw+L5T4uWpsOv8Snmc0BRVE=
                          betslip: >-
                            https://sportsbook.fanduel.com/addToBetslip?marketId=734.190243703&selectionId=43010249&marketId=734.191320429&selectionId=12194340
                    trueStartTime: null
                    trueEndTime: null
                not_priceable:
                  summary: Legs found, but the bookmaker will not price them
                  description: >-
                    The legs exist, so they keep an entry: inactive, `price: 1`,
                    and `meta.inactiveReason` saying why (`not_combinable`,
                    `sgp_unsupported`, `sgp_fetch_failed`, `fixture_ended`,
                    `leg_suspended`, `market_closed`, `bookmaker_suspended`).
                    `not_combinable` — the book will not combine these
                    particular legs — is the most common in practice.
                  value:
                    fixtureId: id1400003171515924
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
                        id1400003171515924:bet365:141162:0:
                          outcomeId: 141162
                          playerId: 0
                          price: 1.57
                          active: true
                          changedAt: 1792443907432
                          bookmaker: bet365
                          marketId: 141161
                          mainLine: true
                          bookmakerMarketId: '121207'
                          bookmakerOutcomeId: '200619007'
                          priceFractional: 20/33
                          priceAmerican: -175
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
                    sgp: {}
                    trueStartTime: null
                    trueEndTime: null
                leg_missing:
                  summary: A leg could not be found
                  description: >-
                    The bookmaker gets NO entry — `sgp` comes back `{}` and
                    `missingOddsIds` names the legs that were not found.
                  value:
                    fixtureId: id1400003171515924
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
                    sgp: {}
                    trueStartTime: null
                    trueEndTime: null
        '400':
          $ref: '#/components/responses/ApiError'
        '401':
          $ref: '#/components/responses/ApiError'
        '403':
          $ref: '#/components/responses/ApiError'
        '404':
          $ref: '#/components/responses/ApiError'
        '422':
          $ref: '#/components/responses/ValidationError'
        '429':
          $ref: '#/components/responses/ApiError'
        '501':
          $ref: '#/components/responses/ApiError'
        '502':
          $ref: '#/components/responses/ApiError'
components:
  schemas:
    FixtureSgpResponse:
      allOf:
        - $ref: '#/components/schemas/Fixture'
        - type: object
          properties:
            odds:
              allOf:
                - $ref: '#/components/schemas/OddsByBookmaker'
              description: >-
                The requested legs and nothing else — inactive ones included (no
                `activeOnly` filtering).
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
            sgp:
              $ref: '#/components/schemas/SgpByBookmaker'
          required:
            - odds
            - bookmakers
            - sgp
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
    SgpByBookmaker:
      type: object
      description: >-
        bookmakerSlug -> (parlayId -> SgpEntry). `parlayId` is built exactly as
        on `/fixtures/odds/parlay`, so the two are directly comparable. A
        bookmaker is omitted — leaving `{}` — when its legs were not all found,
        or when they were but its own pricing service could not quote the parlay
        (in which case there is no `missingOddsIds`).
      additionalProperties:
        type: object
        additionalProperties:
          $ref: '#/components/schemas/SgpEntry'
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
    SgpEntry:
      type: object
      description: >-
        A bookmaker-quoted, correlation-aware parlay price. Same shape as
        ParlayEntry plus the fields the bookmaker returns with its quote.
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
            The bookmaker's correlated price — usually below `singlesPrice`; the
            gap is the correlation margin.
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
        limit:
          type: number
          description: Max stake the bookmaker accepts at this price.
        bookmakerParlayId:
          type: string
          description: The bookmaker's own identifier for this parlay.
        bookmakerChangedAt:
          type: integer
          description: The bookmaker's own quote timestamp, UTC epoch milliseconds.
        betslip:
          type: string
          description: Deep link that opens the bookmaker's betslip with every leg loaded.
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
                - sgp_unsupported
                - sgp_fetch_failed
                - fixture_ended
              description: >-
                Why `active` is false. Adds three SGP-only reasons to the
                `/fixtures/odds/parlay` set.
            offers:
              type: array
              description: >-
                An exchange's remaining price-ladder rungs below the best one,
                which `price`/`limit` already hold.
              items:
                type: object
                properties:
                  price:
                    type: number
                  limit:
                    type: number
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