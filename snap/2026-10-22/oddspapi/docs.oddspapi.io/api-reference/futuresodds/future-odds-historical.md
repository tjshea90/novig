> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Future Odds Historical

> Get historical odds changes for a future.

Lookup mode: required `futureId` + (`bookmakers` required when `oddsIds` is not provided) OR `oddsIds`.



## OpenAPI

````yaml /api-reference/openapi.json get /futures/odds/historical
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
  /futures/odds/historical:
    get:
      tags:
        - futures/odds
      summary: Future Odds Historical
      description: >-
        Get historical odds changes for a future.


        Lookup mode: required `futureId` + (`bookmakers` required when `oddsIds`
        is not provided) OR `oddsIds`.
      operationId: futures_odds_historical__lang__futures_odds_historical_get
      parameters:
        - $ref: '#/components/parameters/futureIdStringRequired'
        - name: bookmakers
          in: query
          required: false
          schema:
            anyOf:
              - type: string
              - type: 'null'
          description: >-
            Comma/space-separated bookmaker slugs. RULE: If oddsIds is NOT
            provided, you must pass exactly ONE bookmaker.
        - name: oddsIds
          in: query
          required: false
          schema:
            anyOf:
              - type: string
              - type: 'null'
          description: 'Optional: comma/space-separated oddsIds to restrict the response.'
      responses:
        '200':
          description: Returns FutureOddsHistoricalResponse
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/FutureOddsHistoricalResponse'
              examples:
                example:
                  summary: Future historical odds
                  value:
                    futureId: pm6980065388006896364
                    odds:
                      polymarket:
                        pm6980065388006896364:polymarket:0:5432:
                          '1776000000000':
                            bookmaker: polymarket
                            participantId: 5432
                            price: 3.2
                            active: true
                            changedAt: 1776000000000
                          '1776667000000':
                            bookmaker: polymarket
                            participantId: 5432
                            price: 3.8
                            active: true
                            changedAt: 1776667000000
                    bookmakers:
                      polymarket:
                        bookmaker: polymarket
                        bookmakerFutureId: '17858'
                        participantsRotated: false
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
    FutureOddsHistoricalResponse:
      type: object
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