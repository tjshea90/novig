> ## Documentation Index
> Fetch the complete documentation index at: https://docs.oddspapi.io/llms.txt
> Use this file to discover all available pages before exploring further.

# Media Bookmaker

> Get bookmaker logo.

Returns an HTTP redirect to the image asset.

Response: 302 with `Location` header (final resource typically WebP).



## OpenAPI

````yaml /api-reference/openapi.json get /media/bookmakers/{slug}
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
  /media/bookmakers/{slug}:
    get:
      tags:
        - media
      summary: Media Bookmaker
      description: |-
        Get bookmaker logo.

        Returns an HTTP redirect to the image asset.

        Response: 302 with `Location` header (final resource typically WebP).
      operationId: media_bookmaker__lang__media_bookmakers__slug__get
      parameters:
        - $ref: '#/components/parameters/slugPath'
      responses:
        '200':
          description: >-
            Final image response (when redirect is followed and/or proxy mode is
            used).
          content:
            image/webp:
              schema:
                type: string
                format: binary
        '302':
          $ref: '#/components/responses/MediaRedirect'
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
    slugPath:
      name: slug
      in: path
      required: true
      schema:
        type: string
      description: Bookmaker slug (e.g. bet365).
  responses:
    MediaRedirect:
      description: Redirect to an asset URL. Follow the Location header.
      headers:
        Location:
          description: Absolute URL to the image asset
          schema:
            type: string
            format: uri
          examples:
            example:
              value: https://ASSET_HOST/categories/de.svg
      content:
        text/plain:
          schema:
            type: string
          examples:
            redirect_body:
              value: ''
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
  schemas:
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