> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Get Bookmakers

> List bookmakers.

Returns the bookmaker catalog available to the provided apiKey.

Lookup mode: `bookmakers` (optional filter list) + `playerProps` (optional capability filter).



## OpenAPI

````yaml /api-reference/openapi.json get /bookmakers
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
  /bookmakers:
    get:
      tags:
        - common
      summary: Get Bookmakers
      description: >-
        List bookmakers.


        Returns the bookmaker catalog available to the provided apiKey.


        Lookup mode: `bookmakers` (optional filter list) + `playerProps`
        (optional capability filter).
      operationId: get_bookmakers__lang__bookmakers_get
      parameters:
        - name: bookmakers
          in: query
          required: false
          schema:
            anyOf:
              - type: string
              - type: 'null'
          description: >-
            Comma- or space-separated bookmaker slugs. Supports `*` / `all` for
            wildcard.
        - name: playerProps
          in: query
          required: false
          schema:
            anyOf:
              - type: boolean
              - type: 'null'
          description: >-
            If true, only return bookmakers that support player proposition
            markets.
      responses:
        '200':
          description: Returns Bookmaker[]
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/BookmakerList'
              examples:
                example:
                  summary: Bookmaker list
                  value:
                    - slug: 188bet
                      bookmakerName: 188BET
                      active: true
                      domain: https://188bet.com
                      serverGroup: at4
                      price: 10
                      websocketPregame: true
                      websocketLive: false
                      maxDelayPregameInSec: 80
                      maxDelayPregameMainInSec: 60
                      availableCountries:
                        - ag
                        - al
                        - am
                        - ar
                        - at
                    - slug: 1xbet
                      bookmakerName: 1xBet
                      active: true
                      domain: https://1xbet.com
                      serverGroup: de4
                      price: 10
                      websocketPregame: true
                      websocketLive: true
                      maxDelayPregameInSec: 30
                      maxDelayLiveInSec: 4
                      maxDelayPregameMainInSec: 6
                      availableCountries:
                        - bo
                        - kr
                        - tn
                        - in
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
    BookmakerList:
      type: array
      items:
        $ref: '#/components/schemas/Bookmaker'
    Bookmaker:
      type: object
      properties:
        slug:
          type: string
          description: >-
            Stable bookmaker slug — the key used in `bookmakers` filters and in
            all odds payloads.
        bookmakerName:
          type: string
          description: Bookmaker display name.
        active:
          type: boolean
          description: Whether the bookmaker is currently served by the API.
        domain:
          type:
            - string
            - 'null'
          format: uri
          description: Main public website URL of the bookmaker.
        serverGroup:
          type:
            - string
            - 'null'
          description: Deprecated internal field — safe to ignore for API consumers.
          deprecated: true
        price:
          type:
            - integer
            - 'null'
          description: Deprecated internal field — safe to ignore for API consumers.
          deprecated: true
        websocketPregame:
          type:
            - boolean
            - 'null'
          description: >-
            Whether pregame odds for this bookmaker are streamed over the
            WebSocket.
        websocketLive:
          type:
            - boolean
            - 'null'
          description: >-
            Whether live (in-play) odds for this bookmaker are streamed over the
            WebSocket.
        playerProps:
          type:
            - boolean
            - 'null'
          description: Whether this bookmaker offers player proposition markets.
        maxDelayPregameInSec:
          type:
            - number
            - 'null'
          description: >-
            Maximum expected refresh delay for pregame odds, in seconds — the
            freshness bound for this bookmaker. Measured from observed vs.
            bookmaker change times (currently the rolling p50) where the book
            publishes usable timestamps, estimated otherwise.
        maxDelayLiveInSec:
          type:
            - number
            - 'null'
          description: >-
            Maximum expected refresh delay for live odds, in seconds. Measured
            from observed vs. bookmaker change times (currently the rolling p50)
            where the book publishes usable timestamps, estimated otherwise.
        maxDelayPregameMainInSec:
          type:
            - number
            - 'null'
          description: >-
            Maximum expected refresh delay for pregame main lines, in seconds.
            Measured from observed vs. bookmaker change times (currently the
            rolling p50) where the book publishes usable timestamps, estimated
            otherwise.
        availableCountries:
          type:
            - array
            - 'null'
          items:
            type: string
          description: >-
            ISO 3166-1 alpha-2 country codes where the bookmaker accepts
            customers.
      required:
        - slug
        - bookmakerName
        - active
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