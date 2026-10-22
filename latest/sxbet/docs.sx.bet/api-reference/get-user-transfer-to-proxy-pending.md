> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Pending deposit

> List open deposits to your proxy wallet by session id.

`GET /user/transfer-to-proxy/pending` returns the `sessionIds` of every deposit started by
[`POST /user/transfer-to-proxy`](/api-reference/post-user-transfer-to-proxy) that is still open,
newest first. For the outcome of one deposit, poll
[`GET /user/transfer-to-proxy/status`](/api-reference/get-user-transfer-to-proxy-status)
with that id.

> [!WARNING]
>
> **An empty `sessionIds` list does not mean the deposit succeeded.** It clears on success and on
> failure alike. Use status for `SUCCESS` / `FAILED`, then confirm the amount with
> [`GET /user/balance-v3`](/api-reference/get-user-balance-v3) if you need the balance.


## OpenAPI

````yaml GET /user/transfer-to-proxy/pending
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
  /user/transfer-to-proxy/pending:
    get:
      tags:
        - Connection
      summary: Check a pending deposit
      description: >-
        Lists every unresolved deposit started with `POST
        /user/transfer-to-proxy` for the authenticated account, newest first.
        There is no path or query param.


        **An empty `sessionIds` array does not mean the last deposit
        succeeded.** The list clears on success and on failure alike. For the
        outcome of a specific deposit, poll `GET /user/transfer-to-proxy/status`
        with that deposit's `sessionId`. Each id is the permit digest returned
        by the fund call.
      operationId: getPendingTransferToProxy
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
                      sessionIds:
                        type: array
                        items:
                          type: string
                        description: >-
                          Permit EIP-712 digests of open deposits, hex without
                          the 0x prefix, newest first. Empty when nothing is
                          open — use `GET /user/transfer-to-proxy/status` for
                          the outcome of a known `sessionId`.
              example:
                status: success
                data:
                  sessionIds:
                    - >-
                      9c8f5b21e4d0a7c3f18b6e94d2a05c7318fa4b62d9e0173c5a8b2f4e6019d3c7
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