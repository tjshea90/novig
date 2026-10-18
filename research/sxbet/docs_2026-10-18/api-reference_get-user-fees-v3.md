> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get your fees

> Get the fee rates that apply to your account from the SX Bet API.

`GET /user/fees-v3` returns the five fee rates that apply to your account. Rates are set per account.
[Fees](/developers/fees) covers what each rate is charged on, and when.

Every rate is a decimal fraction string: `"0.05"` is 5%, `"0.005"` is 0.5%.


## OpenAPI

````yaml GET /user/fees-v3
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
  /user/fees-v3:
    get:
      tags:
        - Connection
      summary: Get your fees
      description: >-
        The five fee rates that apply to your account. Every rate is a decimal
        fraction string, so `"0.05"` is 5%. `null` means the rate is unset and
        nothing is charged.
      operationId: getUserFeesV3
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
                      refundFee:
                        type: string
                        nullable: true
                        description: >-
                          Rate taken on a capital-efficiency refund, at fill
                          time. Decimal fraction string; `null` when unset.
                      makerPayoutFee:
                        type: string
                        nullable: true
                        description: >-
                          Rate taken on profit for a winning single bet where
                          you were the maker. Decimal fraction string; `null`
                          when unset.
                      takerPayoutFee:
                        type: string
                        nullable: true
                        description: >-
                          Rate taken on profit for a winning single bet where
                          you were the taker. Decimal fraction string; `null`
                          when unset.
                      makerParlayPayoutFee:
                        type: string
                        nullable: true
                        description: >-
                          Rate taken on profit for a winning parlay where you
                          were the maker. Decimal fraction string; `null` when
                          unset.
                      takerParlayPayoutFee:
                        type: string
                        nullable: true
                        description: >-
                          Rate taken on profit for a winning parlay where you
                          were the taker. Decimal fraction string; `null` when
                          unset.
              example:
                status: success
                data:
                  refundFee: '0.005'
                  makerPayoutFee: '0.01'
                  takerPayoutFee: '0.02'
                  makerParlayPayoutFee: '0.03'
                  takerParlayPayoutFee: '0.04'
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