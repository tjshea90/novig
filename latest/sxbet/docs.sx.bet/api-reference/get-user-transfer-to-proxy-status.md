> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Deposit status

> Get the outcome of a proxy deposit by session id.

`GET /user/transfer-to-proxy/status` returns `PENDING`, `SUCCESS`, or `FAILED` for one deposit
started by [`POST /user/transfer-to-proxy`](/api-reference/post-user-transfer-to-proxy).
Pass the `sessionId` from that submit response.


## OpenAPI

````yaml GET /user/transfer-to-proxy/status
openapi: 3.0.1
info:
  title: SX Bet API
  version: 1.0.0
  description: >-
    REST API for the SX Bet decentralized sports betting exchange. Retrieve
    sports data, markets, and orderbook information. Post, cancel, and fill
    orders with signed payloads.


    Base URLs:

    - **Mainnet**: `https://api.sx.bet`

    - **Testnet**: `https://api.toronto.sx.bet`
servers:
  - url: https://api.sx.bet
    description: Mainnet (SX Network, chainId 4162)
  - url: https://api.toronto.sx.bet
    description: Testnet (Toronto, chainId 79479957)
security: []
tags:
  - name: Connection
    description: Server metadata and heartbeat management
  - name: Sports Data
    description: Sports, leagues, teams, fixtures, and live scores
  - name: Markets
    description: Active, specific, and popular betting markets
  - name: Trades
    description: Matched trades and portfolio history
  - name: Orders
    description: Orderbook queries, posting, cancelling, and filling orders
paths:
  /user/transfer-to-proxy/status:
    get:
      tags:
        - Connection
      summary: Deposit status by session
      description: >-
        Returns the outcome of one deposit started with `POST
        /user/transfer-to-proxy`, keyed by the `sessionId` (permit digest) from
        that response. Poll until `status` is `SUCCESS` or `FAILED`. The deposit
        must belong to the authenticated account.
      operationId: getTransferToProxyStatus
      parameters:
        - name: sessionId
          in: query
          required: true
          schema:
            type: string
          description: >-
            Permit EIP-712 digest from the fund response, hex without the 0x
            prefix.
      responses:
        '200':
          description: ''
          content:
            application/json:
              schema:
                type: object
                properties:
                  status:
                    type: string
                    description: '`success` or `failure` if the request succeeded or not'
                  data:
                    type: object
                    properties:
                      sessionId:
                        type: string
                        description: Echo of the queried permit digest.
                      status:
                        type: string
                        enum:
                          - PENDING
                          - SUCCESS
                          - FAILED
                        description: >-
                          `PENDING` while the deposit is in flight; `SUCCESS` or
                          `FAILED` when settled.
              example:
                status: success
                data:
                  sessionId: >-
                    9c8f5b21e4d0a7c3f18b6e94d2a05c7318fa4b62d9e0173c5a8b2f4e6019d3c7
                  status: PENDING
        '400':
          description: Unknown `sessionId`, or the deposit is not yours.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: TRANSFER_NOT_FOUND
                error: Bad Request
                statusCode: 400
        '401':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: BAD_AUTH
                error: Unauthorized
                statusCode: 401
        '503':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: Service Unavailable
                statusCode: 503
      security:
        - SxApiKey: []
components:
  schemas:
    ErrorNest:
      description: Most common shape.
      type: object
      properties:
        message:
          type: string
          example: INVALID_USER
        error:
          type: string
          example: Unauthorized
        statusCode:
          type: integer
          example: 401
  securitySchemes:
    SxApiKey:
      type: apiKey
      in: header
      name: x-sx-api-key
      description: 'API key, sent as the `x-sx-api-key` header. '

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.