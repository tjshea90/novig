> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Pending proxy deployment

> Check whether a proxy wallet deployment is still pending.

`GET /user/pending-deploy-proxy` is one half of confirming a deployment started by
[`POST /user/deploy-proxy`](/api-reference/post-user-deploy-proxy).

> [!WARNING]
>
> **`pending: false` does not mean the deployment succeeded.** It goes false on success and on failure
> alike, because the query behind it selects only rows still marked `PENDING`. Read `deployed` on
> [`GET /user/proxy`](/api-reference/get-user-proxy) alongside it: `pending: false` with
> `deployed: false` means the deployment failed and should be retried. [Proxy
> wallet](/developers/accounts.md#waiting-for-deployment) has the poll loop.


## OpenAPI

````yaml GET /user/pending-deploy-proxy
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
  /user/pending-deploy-proxy:
    get:
      tags:
        - Wallet & Funding
      summary: Pending proxy deployment
      description: >-
        Whether a deployment started with `POST /user/deploy-proxy` is still in
        flight for the authenticated account. Takes no path or query param.


        **`pending: false` does not mean the deployment succeeded.** The flag
        clears on success and on failure alike. Read `deployed` on `GET
        /user/proxy` alongside it: `pending: false` with `deployed: false` means
        the deployment failed and should be retried.
      operationId: getPendingDeployProxy
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
                      pending:
                        type: boolean
                        description: >-
                          True while a deployment is still in flight. False on
                          success and on failure alike.
                      sessionId:
                        type: string
                        nullable: true
                        description: >-
                          The in-flight deployment's session id, or null when
                          nothing is pending.
              example:
                status: success
                data:
                  pending: true
                  sessionId: 3f1c9d64-0a2e-4b77-9a51-2c0f6e8d4b31
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