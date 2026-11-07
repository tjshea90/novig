> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Fixture Odds

> Get current odds for a fixture.

Lookup mode: required `fixtureId` + optional `bookmakers`, optional filtering flags.

If `since` is provided, returns odds updates with `changedAt >= since`.



## OpenAPI

````yaml /api-reference/openapi.json get /fixtures/odds
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
  /fixtures/odds:
    get:
      tags:
        - fixtures/odds
      summary: Fixture Odds
      description: >-
        Get current odds for a fixture.


        Lookup mode: required `fixtureId` + optional `bookmakers`, optional
        filtering flags.


        If `since` is provided, returns odds updates with `changedAt >= since`.
      operationId: fixtures_odds__lang__fixtures_odds_get
      parameters:
        - $ref: '#/components/parameters/fixtureIdStringRequired'
        - name: bookmakers
          in: query
          required: false
          schema:
            anyOf:
              - type: string
              - type: 'null'
          description: 'Comma/space-separated bookmaker slugs (default: all).'
        - name: since
          in: query
          required: false
          schema:
            anyOf:
              - type: integer
              - type: 'null'
          description: changedAt >= since (ms)
        - name: marketActive
          in: query
          required: false
          schema:
            anyOf:
              - type: boolean
              - type: 'null'
          description: If set, filter by marketActive flag.
        - name: mainLine
          in: query
          required: false
          schema:
            anyOf:
              - type: boolean
              - type: 'null'
          description: If set, filter by mainLine flag.
      responses:
        '200':
          description: Returns FixtureOddsResponse (fixture meta + odds + bookmaker meta)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/FixtureOddsResponse'
              examples:
                example:
                  summary: Fixture odds
                  value:
                    fixtureId: id1100013273682356
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
                    trueStartTime: null
                    trueEndTime: null
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
                      flashscoreId: prbYk4Kq
                      mollybetId: 2026-10-20,29093,29094
                      oddinId: null
                      opticoddsId: 20261020D42F1162
                      pinnacleId: 1633801639
                      sofascoreId: 16814811
                      lsportsId: 19766754
                      txoddsId: null
                    odds:
                      pinnacle:
                        id1100013273682356:pinnacle:111470:0:
                          outcomeId: 111470
                          playerId: 0
                          price: 1.689
                          active: true
                          changedAt: 1792444223199
                          bookmaker: pinnacle
                          limit: 326
                          marketId: 111470
                          marketActive: true
                          mainLine: false
                          bookmakerMarketId: >-
                            altLine/4/487/1633801639/3772269294/57414014940/0/teamTotal
                          bookmakerOutcomeId: home/116.5/over
                          bookmakerChangedAt: 1792444222717
                          priceFractional: 31/45
                          priceAmerican: -145
                        id1100013273682356:pinnacle:111471:0:
                          outcomeId: 111471
                          playerId: 0
                          price: 2.19
                          active: true
                          changedAt: 1792444223199
                          bookmaker: pinnacle
                          limit: 225
                          marketId: 111470
                          marketActive: true
                          mainLine: false
                          bookmakerMarketId: >-
                            altLine/4/487/1633801639/3772269294/57414014940/0/teamTotal
                          bookmakerOutcomeId: home/116.5/under
                          bookmakerChangedAt: 1792444222717
                          priceFractional: 119/100
                          priceAmerican: 119
                    bookmakers:
                      pinnacle:
                        bookmaker: pinnacle
                        bookmakerFixtureId: '1633801639'
                        fixturePath: https://www.pinnacle.com/en/e/e/e/1633801639
                        hasOdds: true
                        staleOdds: false
                        staleOddsResponseCode: 502
                        suspended: false
                        participantsRotated: false
                        meta: null
                        updatedAt: '2026-10-18T13:00:45.998318+00:00'
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
components:
  parameters:
    fixtureIdStringRequired:
      name: fixtureId
      in: query
      required: true
      schema:
        type: string
      description: Single fixture ID (e.g. id1400003171515924).
  schemas:
    FixtureOddsResponse:
      allOf:
        - $ref: '#/components/schemas/Fixture'
        - type: object
          properties:
            odds:
              $ref: '#/components/schemas/OddsByBookmaker'
          required:
            - odds
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
    OddsMap:
      type: object
      additionalProperties:
        $ref: '#/components/schemas/OddQuote'
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