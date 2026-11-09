> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Future Mapping

> Map internal future IDs to bookmaker/provider future IDs.

Lookup mode: required `bookmaker` + (`futureIds` OR `bookmakerFutureIds`).



## OpenAPI

````yaml /api-reference/openapi.json get /futures/mapping
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
  /futures/mapping:
    get:
      tags:
        - mapping
      summary: Future Mapping
      description: >-
        Map internal future IDs to bookmaker/provider future IDs.


        Lookup mode: required `bookmaker` + (`futureIds` OR
        `bookmakerFutureIds`).
      operationId: future_mapping__lang__futures_mapping_get
      parameters:
        - name: bookmaker
          in: query
          required: true
          schema:
            type: string
          description: Single bookmaker slug.
        - name: futureIds
          in: query
          required: false
          schema:
            anyOf:
              - type: string
              - type: 'null'
          description: Comma/space-separated internal futureIds.
        - name: bookmakerFutureIds
          in: query
          required: false
          schema:
            anyOf:
              - type: string
              - type: 'null'
          description: Comma/space-separated provider future IDs.
      responses:
        '200':
          description: Returns FutureMapping[]
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/FutureMappingList'
              examples:
                example:
                  summary: Future mapping rows
                  value:
                    - bookmaker: pinnacle
                      futureId: id100000261286891
                      bookmakerFutureId: null
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
  schemas:
    FutureMappingList:
      type: array
      items:
        $ref: '#/components/schemas/FutureMapping'
    FutureMapping:
      type: object
      properties:
        bookmaker:
          type: string
          description: Bookmaker slug.
        futureId:
          type:
            - string
            - 'null'
          description: OddsPapi future identifier.
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