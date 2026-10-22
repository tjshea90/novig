> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Deploy a proxy wallet

> Deploy a proxy wallet for your account on SX Bet.

Required in order to trade. This call is asynchronous and will complete in a few seconds.

To check whether the deployment has completed, poll
[`GET /user/proxy`](/api-reference/get-user-proxy) and wait for `deployed` to be `true`.
Until then, [`POST /orders-v3`](/api-reference/post-orders-v3) and
[`POST /user/transfer-to-proxy`](/developers/funding) reject with `PROXY_NOT_DEPLOYED`.


## OpenAPI

````yaml POST /user/deploy-proxy
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
  /user/deploy-proxy:
    post:
      tags:
        - Connection
      summary: Deploy a proxy wallet
      description: >-
        Enqueues deployment of your proxy wallet. Trading is proxy-only, so this
        is a mandatory first step — `POST /orders-v3` returns
        `PROXY_NOT_DEPLOYED` without it, bet credits included.
      operationId: deployUserProxy
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
                        nullable: true
                        description: >-
                          Job id for the asynchronous deployment, or null when
                          nothing was enqueued.
                      status:
                        type: string
                        enum:
                          - PENDING
                          - SUCCESS
                      proxyWalletAddress:
                        type: string
                        description: >-
                          Your proxy address. Deterministic on your user
                          address, so it is correct whether or not the
                          deployment has completed.
              example:
                status: success
                data:
                  sessionId: 3f1c9d64-0a2e-4b77-9a51-2c0f6e8d4b31
                  status: PENDING
                  proxyWalletAddress: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
        '400':
          description: ''
          content:
            application/json:
              schema:
                oneOf:
                  - $ref: '#/components/schemas/ErrorNestArray'
                  - $ref: '#/components/schemas/ErrorNest'
              example:
                message:
                  - address must be a valid address
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
        '500':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: Failed to deploy proxy
                error: Internal Server Error
                statusCode: 500
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
    ErrorNestArray:
      description: >-
        Validation errors. Same envelope as ErrorNest but `message` is an array
        — normalise both.
      type: object
      properties:
        message:
          type: array
          items:
            type: string
          example:
            - 'range must be one of the following values: 1, 7, 30'
        error:
          type: string
          example: Bad Request
        statusCode:
          type: integer
          example: 400
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