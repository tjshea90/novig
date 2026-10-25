> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Get Seasons

> List seasons.

Lookup mode: `seasonIds` OR `tournamentId`.



## OpenAPI

````yaml /api-reference/openapi.json get /seasons
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
  /seasons:
    get:
      tags:
        - common
      summary: Get Seasons
      description: |-
        List seasons.

        Lookup mode: `seasonIds` OR `tournamentId`.
      operationId: get_seasons__lang__seasons_get
      parameters:
        - name: seasonIds
          in: query
          required: false
          schema:
            anyOf:
              - type: string
              - type: 'null'
          description: Comma- or space-separated season IDs.
        - name: tournamentId
          in: query
          required: false
          schema:
            anyOf:
              - type: integer
              - type: 'null'
          description: Tournament ID.
      responses:
        '200':
          description: Returns Season[]
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/SeasonList'
              examples:
                example:
                  summary: Season list
                  value:
                    - seasonId: 119257
                      tournamentId: 132
                      sportId: 11
                      seasonSlug: nba-24-25
                      seasonName: NBA 24/25
                    - seasonId: 131631
                      tournamentId: 132
                      sportId: 11
                      seasonSlug: nba-25-26
                      seasonName: NBA 25/26
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
    SeasonList:
      type: array
      items:
        $ref: '#/components/schemas/Season'
    Season:
      type: object
      properties:
        seasonId:
          type: integer
          description: Numeric season identifier.
        tournamentId:
          type: integer
          description: Tournament this season belongs to.
        sportId:
          type:
            - integer
            - 'null'
          description: Sport the season belongs to.
        seasonSlug:
          type:
            - string
            - 'null'
          description: URL-safe season slug.
        seasonName:
          type: string
          description: Season display name, translated per the request language prefix.
      required:
        - seasonId
        - tournamentId
        - seasonName
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