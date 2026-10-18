> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Set heartbeat

> Register or refresh a heartbeat timer for your proxy wallet.

`POST /heartbeat/v3` registers or refreshes a heartbeat timer for account.

| | |
| - | - |
| Auth | **API key only** (`x-sx-api-key`) |
| Scope | The proxy wallet owned by the key's address |
| Refresh | Call again to push `expiresAt` forward |

## Request

| Field | Type | Rule |
| - | - | - |
| `timeoutSeconds` | integer | Required. `0`–`3600`. `0` clears an armed timer |

## Response

`200 OK` returns the armed heartbeat:

| Field | Meaning |
| - | - |
| `proxyAddress` | The deployed proxy the heartbeat is armed for |
| `timeoutSeconds` | The timeout that was registered |
| `expiresAt` | When the timer lapses (`now + timeoutSeconds`) |

## Errors

| Status | When |
| - | - |
| `400` | `INVALID_PROXY` — no deployed proxy for your address; or `timeoutSeconds` outside `0`–`3600` |
| `401` | Missing or invalid API key (`BAD_AUTH`) |
| `503` | OBv3 is unavailable on this environment |

<Note>
  **You need a deployed proxy first.** If your proxy is not yet deployed the call returns
  `400 INVALID_PROXY`. Deploy it with [`POST /user/deploy-proxy`](/api-reference/post-user-deploy-proxy).
</Note>


## OpenAPI

````yaml POST /heartbeat/v3
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
  /heartbeat/v3:
    post:
      tags:
        - Orders
      summary: Set heartbeat
      description: >-
        Registers or refreshes a heartbeat timer for your deployed proxy wallet.
        The owner (and its proxy) is derived from the API key — there is no
        address in the body. `expiresAt` is returned as `now + timeoutSeconds`.
        Send `timeoutSeconds: 0` to clear an armed timer. Registering a
        heartbeat does not by itself cancel your orders — see
        [Heartbeat](/developers/heartbeat).
      operationId: setHeartbeatV3
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - timeoutSeconds
              properties:
                timeoutSeconds:
                  type: integer
                  description: >-
                    Seconds until the heartbeat expires. `0` clears an armed
                    timer.
                  minimum: 0
                  maximum: 3600
            example:
              timeoutSeconds: 300
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
                      proxyAddress:
                        type: string
                        description: The deployed proxy wallet the heartbeat is armed for.
                      timeoutSeconds:
                        type: integer
                        description: The timeout that was registered.
                      expiresAt:
                        type: string
                        format: date-time
                        description: 'When the heartbeat lapses: `now + timeoutSeconds`.'
              example:
                status: success
                data:
                  proxyAddress: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                  timeoutSeconds: 300
                  expiresAt: '2026-08-06T04:35:00.000Z'
        '400':
          description: ''
          content:
            application/json:
              schema:
                oneOf:
                  - $ref: '#/components/schemas/ErrorNestArray'
                  - $ref: '#/components/schemas/ErrorNest'
              example:
                message: INVALID_PROXY
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