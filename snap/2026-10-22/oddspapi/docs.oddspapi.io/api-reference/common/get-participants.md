> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Get Participants

> List participants.

Lookup mode: `participantIds` OR `sportId` OR `playerId`.



## OpenAPI

````yaml /api-reference/openapi.json get /participants
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
  /participants:
    get:
      tags:
        - common
      summary: Get Participants
      description: |-
        List participants.

        Lookup mode: `participantIds` OR `sportId` OR `playerId`.
      operationId: get_participants__lang__participants_get
      parameters:
        - name: participantIds
          in: query
          required: false
          schema:
            anyOf:
              - type: string
              - type: 'null'
          description: Comma- or space-separated participant IDs.
        - name: sportId
          in: query
          required: false
          schema:
            anyOf:
              - type: integer
              - type: 'null'
          description: Single sport ID.
        - name: playerId
          in: query
          required: false
          schema:
            anyOf:
              - type: integer
              - type: 'null'
          description: Single player ID.
      responses:
        '200':
          description: Returns Participant[]
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ParticipantList'
              examples:
                example:
                  summary: Participants list
                  value:
                    - participantId: 3409
                      sportId: 11
                      name: Chicago Bulls
                      participantShortName: Chicago
                      participantAbbr: CHI
                    - participantId: 3410
                      sportId: 11
                      name: Milwaukee Bucks
                      participantShortName: Milwaukee
                      participantAbbr: MIL
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
    ParticipantList:
      type: array
      items:
        $ref: '#/components/schemas/Participant'
    Participant:
      type: object
      properties:
        participantId:
          type: integer
          description: Numeric participant (team/individual) identifier.
        sportId:
          type:
            - integer
            - 'null'
          description: Sport the participant belongs to.
        name:
          type:
            - string
            - 'null'
          description: >-
            Full participant name, translated per the request language prefix.
            This field will be renamed to `participantName` in a future release.
        participantShortName:
          type:
            - string
            - 'null'
          description: Short participant name.
        participantMediumName:
          type:
            - string
            - 'null'
          description: Medium-length participant name.
        participantAbbr:
          type:
            - string
            - 'null'
          description: Participant abbreviation (e.g. jersey code).
      required:
        - participantId
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