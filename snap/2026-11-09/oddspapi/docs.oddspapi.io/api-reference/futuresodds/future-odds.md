> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Future Odds

> Get latest odds for a future.

Lookup mode: required `futureId` + optional filters (`bookmakers`, `since`, `mainLines`, `includeFuture`).



## OpenAPI

````yaml /api-reference/openapi.json get /futures/odds
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
  /futures/odds:
    get:
      tags:
        - futures/odds
      summary: Future Odds
      description: >-
        Get latest odds for a future.


        Lookup mode: required `futureId` + optional filters (`bookmakers`,
        `since`, `mainLines`, `includeFuture`).
      operationId: futures_odds__lang__futures_odds_get
      parameters:
        - $ref: '#/components/parameters/futureIdStringRequired'
        - name: bookmakers
          in: query
          required: false
          schema:
            anyOf:
              - type: string
              - type: 'null'
          description: 'Comma/space-separated bookmaker slugs (default: all)'
        - name: since
          in: query
          required: false
          schema:
            anyOf:
              - type: integer
              - type: 'null'
          description: >-
            changedAt >= since (ms). Consistent with fixtures: all odds-update
            times are epoch milliseconds.
        - name: mainLines
          in: query
          required: false
          schema:
            type: boolean
            default: false
          description: Only return main lines
        - name: includeFuture
          in: query
          required: false
          schema:
            type: boolean
            default: false
          description: If true, includes futureMeta in the response.
      responses:
        '200':
          description: Returns FutureOddsResponse
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/FutureOddsResponse'
              examples:
                example:
                  summary: Future odds
                  value:
                    futureId: pm6980065388006896364
                    bookmakers:
                      polymarket:
                        odds:
                          - oddsId: pm6980065388006896364:polymarket:0:5432
                            bookmaker: polymarket
                            futureOutcomeId: 0
                            participantId: 5432
                            price: 3.8
                            active: true
                            changedAt: 1776667441000
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
    futureIdStringRequired:
      name: futureId
      in: query
      required: true
      schema:
        type: string
      description: Single future ID (e.g. id11000132131631).
  schemas:
    FutureOddsResponse:
      type: object
      properties:
        futureId:
          type: string
          description: Future identifier the odds belong to.
        bookmakers:
          type: object
          additionalProperties:
            $ref: '#/components/schemas/FutureBookmakerOdds'
          description: Odds grouped per bookmaker slug.
        futureMeta:
          $ref: '#/components/schemas/FutureMeta'
          description: Future metadata echoed with the odds.
      required:
        - futureId
        - bookmakers
      additionalProperties: true
    FutureBookmakerOdds:
      type: object
      properties:
        odds:
          type: array
          items:
            $ref: '#/components/schemas/FutureOddsRow'
          description: Odds rows of this bookmaker for the future.
      required:
        - odds
      additionalProperties: true
    FutureMeta:
      type: object
      properties:
        futureId:
          type: string
          description: >-
            Future identifier: `{prefix}{sportId}{seasonId}{futureMarketId}` —
            see Core Concepts → Future IDs. Tournament is a field on the future,
            not a segment of this ID.
        reissuedFutureId:
          type:
            - string
            - 'null'
          description: >-
            The future that replaced this one, when a cancelled future has been
            re-listed under a new id. `null` is the normal value: it is only
            ever populated on a cancelled future (`status.statusId` `3`) whose
            replacement already exists in the feed, so permanent cancellations
            stay `null`, and a future can go from `null` to an id after you
            first saw it cancelled. One hop: if the replacement was itself
            cancelled, follow its own `reissuedFutureId`.
        previousFutureId:
          type:
            - string
            - 'null'
          description: >-
            The inverse of `reissuedFutureId`: the cancelled future this one
            replaced. Unlike the forward pointer this sits on a normal open
            future, so a future carries its own history even if you never saw
            the cancelled one. `null` is the normal value. One hop and one
            predecessor at most — follow it repeatedly to walk back to the start
            of a chain. Cleared again if a cancellation is reverted.
        status:
          $ref: '#/components/schemas/Status'
          description: Lifecycle status of the future.
        sport:
          $ref: '#/components/schemas/SportRef'
          description: Sport (or prediction-market topic) reference.
        tournament:
          $ref: '#/components/schemas/TournamentRef'
          description: Tournament reference.
        season:
          $ref: '#/components/schemas/SeasonRef'
          description: Season reference.
        startTime:
          type: integer
          description: Epoch seconds (UTC).
        endTime:
          type: integer
          description: Epoch seconds (UTC).
        market:
          $ref: '#/components/schemas/FutureMarketRef'
          description: Future market reference (e.g. Winner).
        participants:
          type: array
          items:
            type: object
            properties:
              participantId:
                type: integer
              participantName:
                type:
                  - string
                  - 'null'
            required:
              - participantId
            additionalProperties: true
          description: >-
            Participants attached to this future. Not currently populated in
            production — every future returns `[]` (0 of 2,018 sampled across 40
            sports carried any participant), so resolve names via
            `/participants` or the tournament/season metadata instead.
        externalProviders:
          $ref: '#/components/schemas/ExternalProviders'
          description: >-
            Native identifiers of this future at external providers (polymarket,
            kalshi, …).
        bookmakers:
          type: object
          additionalProperties:
            $ref: '#/components/schemas/BookmakerFutureMeta'
          description: >-
            Per-bookmaker metadata for this future. Contains only bookmakers
            that currently offer valid realtime odds (empty object when none
            do).
      required:
        - futureId
        - reissuedFutureId
        - previousFutureId
        - status
        - sport
        - tournament
        - season
        - startTime
        - endTime
        - market
        - participants
        - externalProviders
        - bookmakers
      additionalProperties: true
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
    FutureOddsRow:
      type: object
      description: >-
        A single futures odds entry. The key for futures odds is
        futureId:bookmaker:futureOutcomeId:participantId.
      properties:
        oddsId:
          type: string
          description: >-
            Stable identifier of this odds row. Format:
            `{futureId}:{bookmaker}:{futureOutcomeId}:{participantId}`. Treat
            this value as an opaque string and read individual values from the
            explicit fields on this row rather than splitting it.
        futureOutcomeId:
          type: integer
          description: >-
            Side and line within the future market. Participant-keyed markets
            (winner, topscorer, relegation, mvp) own no outcome rows and carry
            the sentinel `0` — the selection is the participant. Decomposed
            markets (Yes/No, Over/Under) carry a real 6-digit id (>= 100001) per
            side per line; the handicap sits on the outcome, not on the market.
            Never overlaps the futureMarketId space, which is at most 5 digits.
        bookmaker:
          type: string
          description: Bookmaker slug.
        participantId:
          type:
            - integer
            - 'null'
          description: >-
            Participant the outright price refers to, when the selection maps to
            a participant.
        price:
          type: number
          description: Decimal odds.
        active:
          type: boolean
          description: Whether this selection is currently available at the bookmaker.
        bookmakerOutcomeId:
          type:
            - string
            - 'null'
          description: Native bookmaker selection identifier.
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
          description: Bookmaker-specific metadata.
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
        - oddsId
        - bookmaker
      additionalProperties: true
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
    FutureMarketRef:
      type: object
      properties:
        marketId:
          type:
            - integer
            - 'null'
          description: >-
            Future market identifier (e.g. `1` = Winner). Future markets are a
            separate namespace from `/markets`, so this id cannot be resolved
            through that endpoint.
        marketName:
          type:
            - string
            - 'null'
          description: >-
            Future market display name, translated. Not currently populated in
            production — returns `null`.
        marketType:
          type:
            - string
            - 'null'
          description: >-
            Market-type taxonomy value (e.g. `outrights`). Not currently
            populated in production — returns `null`.
      additionalProperties: true
    ExternalProviders:
      type: object
      additionalProperties: true
    BookmakerFutureMeta:
      type: object
      properties:
        bookmaker:
          type: string
          description: Bookmaker slug.
        bookmakerFutureId:
          type:
            - string
            - 'null'
          description: Native future/outright identifier at the bookmaker.
        participantsRotated:
          type: boolean
          description: >-
            True if the bookmaker lists selections in a different order relative
            to OddsPapi.
      required:
        - bookmaker
        - participantsRotated
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