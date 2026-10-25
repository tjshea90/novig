> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Get Markets

> List markets.

Lookup mode: `marketIds` OR `sportId` OR `outcomeIds`.



## OpenAPI

````yaml /api-reference/openapi.json get /markets
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
  /markets:
    get:
      tags:
        - common
      summary: Get Markets
      description: |-
        List markets.

        Lookup mode: `marketIds` OR `sportId` OR `outcomeIds`.
      operationId: get_markets__lang__markets_get
      parameters:
        - name: marketIds
          in: query
          required: false
          schema:
            anyOf:
              - type: string
              - type: 'null'
          description: Comma- or space-separated market IDs.
        - name: sportId
          in: query
          required: false
          schema:
            anyOf:
              - type: integer
              - type: 'null'
          description: Single sport ID.
        - name: outcomeIds
          in: query
          required: false
          schema:
            anyOf:
              - type: string
              - type: 'null'
          description: Comma- or space-separated outcome IDs.
      responses:
        '200':
          description: Returns Market[]
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/MarketList'
              examples:
                example:
                  summary: Markets list
                  value:
                    - marketId: 111
                      marketLength: 2
                      sportId: 11
                      playerProp: false
                      handicap: 0
                      period: result
                      marketType: moneyline
                      marketName: Winner (incl. overtime)
                      marketNameShort: Winner
                      outcomes:
                        - outcomeId: 111
                          outcomeName: '1'
                        - outcomeId: 112
                          outcomeName: '2'
                    - marketId: 113
                      marketLength: 3
                      sportId: 11
                      playerProp: false
                      handicap: 0
                      period: fulltime
                      marketType: 1x2
                      marketName: Regular Time Result
                      marketNameShort: 1X2
                      outcomes:
                        - outcomeId: 113
                          outcomeName: '1'
                        - outcomeId: 114
                          outcomeName: X
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
  schemas:
    MarketList:
      type: array
      items:
        $ref: '#/components/schemas/Market'
    Market:
      type: object
      properties:
        marketId:
          type: integer
          description: >-
            Frozen market identifier — equal to the first (lowest) `outcomeId`
            of the market.
        sportId:
          type:
            - integer
            - 'null'
          description: >-
            Sport the market belongs to (also the first two digits of
            `marketId`).
        marketType:
          type:
            - string
            - 'null'
          description: >-
            Open market-type taxonomy value (e.g. `1x2`, `totals`, `spreads`,
            `players-shots`). Stable per value, never exhaustive.
        period:
          type:
            - string
            - 'null'
          description: >-
            Generic period key the market settles on (see the period vocabulary
            in Enumerations).
        marketLength:
          type:
            - integer
            - 'null'
          description: >-
            Number of outcomes under this marketId - the sides that can be bet
            on, together forming the complete probability space (implied
            probabilities sum to ~1 before margin removal).
        playerProp:
          type:
            - boolean
            - 'null'
          description: >-
            True for player proposition markets — their odds rows carry a
            non-zero `playerId`.
        handicap:
          type:
            - number
            - 'null'
          description: >-
            Handicap / total line of the market. Each line is its own market
            (own `marketId`).
        marketName:
          type:
            - string
            - 'null'
          description: Market display name, translated per the request language prefix.
        marketNameShort:
          type:
            - string
            - 'null'
          description: Short market display name.
        outcomes:
          type: array
          items:
            $ref: '#/components/schemas/MarketOutcome'
          description: The market's outcomes, in their canonical order.
      required:
        - marketId
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
    MarketOutcome:
      type: object
      properties:
        outcomeId:
          type: integer
          description: >-
            Frozen outcome identifier — a fully decomposed selection (marketType
            → period → handicap → side). First two digits = `sportId`.
        outcomeName:
          type:
            - string
            - 'null'
          description: Outcome display name, translated per the request language prefix.
      required:
        - outcomeId
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