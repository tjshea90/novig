> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get your fills

> Get your fill history from the SX Bet API.

`GET /fills-v3` returns **one row per match**, each with its own amount, price and settlement. Where
[`GET /trades-v3`](/api-reference/get-trades-v3) gives one row for a whole bet, this gives the
fills that made it up. A trade will have multiple fills that compose it.

Narrow with `tradeId` (one bet) or `orderId` (the signed order id — same value as `id` on order rows
and `orderId` from [`POST /orders-v3`](/api-reference/post-orders-v3)).

`status` filters to one point in the [bet lifecycle](/developers/bet-lifecycle) — `MATCHED`,
`LOCKED`, `SETTLED` or `FAILED`. A bet that filled against several makers can have its fills in
different states at once, so filtering here is per fill, not per bet: `status=LOCKED` on a bet with
one locked and one still-matched fill returns just the locked one. Combine it with `tradeId` to see
which parts of a single bet have escrowed.


## OpenAPI

````yaml GET /fills-v3
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
  /fills-v3:
    get:
      tags:
        - Trades
      summary: Get your fills
      description: >-
        Your fills at LEG grain — one row per maker/taker pairing. Scoped to the
        authenticated address.
      operationId: getFillsV3
      parameters:
        - name: tradeId
          in: query
          required: false
          schema:
            type: string
          description: Trade ID to query.
        - name: orderId
          in: query
          required: false
          schema:
            type: string
          description: >-
            Signed order ID to query. Same value as `id` on order rows /
            `orderId` from `POST /orders-v3`. An unknown or foreign id returns
            an empty page.
        - name: status
          in: query
          required: false
          schema:
            type: string
            enum:
              - MATCHED
              - LOCKED
              - SETTLED
              - FAILED
          description: Status to filter by.
        - name: startDate
          in: query
          required: false
          schema:
            type: string
            format: date-time
            example: '2026-06-01T00:00:00Z'
          description: Only include fills recorded at or after this time (ISO-8601).
        - name: endDate
          in: query
          required: false
          schema:
            type: string
            format: date-time
            example: '2026-06-08T00:00:00Z'
          description: >-
            Only include fills recorded at or before this time (ISO-8601). Must
            not be earlier than `startDate`.
        - name: sortAsc
          in: query
          required: false
          schema:
            type: boolean
            default: true
          description: Sort oldest-first. Defaults to true.
        - name: perPage
          in: query
          required: false
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 50
          description: Rows per page.
        - name: nextKey
          in: query
          required: false
          schema:
            type: string
          description: Cursor from the previous response to continue pagination.
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
                      fills:
                        type: array
                        items:
                          $ref: '#/components/schemas/FillV3'
                      nextKey:
                        type: string
                        description: >-
                          Pass as `nextKey` to retrieve the next page. Absent —
                          not null — on the last page.
              example:
                status: success
                data:
                  fills:
                    - id: >-
                        0xec8eaaa21ef52bf5f2a3fee5f139258a63f65cf2218dfee40362f2fc90e55793:0x02d22c2ac3f2aa27ca96e156aa2fa4affb15064a169e02b55881bdd6867c55ea:0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266
                      matchId: >-
                        0xec8eaaa21ef52bf5f2a3fee5f139258a63f65cf2218dfee40362f2fc90e55793
                      orderId: >-
                        0x02d22c2ac3f2aa27ca96e156aa2fa4affb15064a169e02b55881bdd6867c55ea
                      tradeId: >-
                        0xac6bb30bba6ea82d8717219a4fa49d15b58fd638fba479f294b4d402376d14d6
                      marketHash: >-
                        0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266
                      isParlay: false
                      userAddress: '0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5'
                      wallet: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                      fillAmount: '333334'
                      fillOdds: '40000000000000000000'
                      returnAmount: '833335'
                      netReturnAmount: '829335'
                      isBettingOutcomeOne: false
                      isMaker: false
                      status: LOCKED
                      txHash: >-
                        0x64307e2f35d0a81b0f44e9043e99cf01def3b2f079fb68cf2c5a942b88d95e9a
                      ceRefundAmount: '0'
                      ceRefundFeeAmount: '0'
                      usedBetCredits: false
                      createdAt: '2026-07-31T18:47:09.586Z'
                      updatedAt: '2026-07-31T18:47:12.837Z'
                      settlement: null
                    - id: >-
                        0x6c2d2a49c159720d26fa89a13ac49711aba2adac5dad2c12255440ebdf7a62c4:0x02d22c2ac3f2aa27ca96e156aa2fa4affb15064a169e02b55881bdd6867c55ea:0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266
                      matchId: >-
                        0x6c2d2a49c159720d26fa89a13ac49711aba2adac5dad2c12255440ebdf7a62c4
                      orderId: >-
                        0x02d22c2ac3f2aa27ca96e156aa2fa4affb15064a169e02b55881bdd6867c55ea
                      tradeId: >-
                        0xac6bb30bba6ea82d8717219a4fa49d15b58fd638fba479f294b4d402376d14d6
                      marketHash: >-
                        0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266
                      isParlay: false
                      userAddress: '0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5'
                      wallet: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                      fillAmount: '666666'
                      fillOdds: '40000000000000000000'
                      returnAmount: '1666665'
                      netReturnAmount: '1658665'
                      isBettingOutcomeOne: false
                      isMaker: false
                      status: LOCKED
                      txHash: >-
                        0x64307e2f35d0a81b0f44e9043e99cf01def3b2f079fb68cf2c5a942b88d95e9a
                      ceRefundAmount: '0'
                      ceRefundFeeAmount: '0'
                      usedBetCredits: false
                      createdAt: '2026-07-31T18:47:09.577Z'
                      updatedAt: '2026-07-31T18:47:12.837Z'
                      settlement: null
        '400':
          description: ''
          content:
            application/json:
              schema:
                oneOf:
                  - $ref: '#/components/schemas/ErrorNestArray'
                  - $ref: '#/components/schemas/ErrorNest'
              example:
                message: Invalid nextKey
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
    FillV3:
      description: 'Leg grain: one row per maker/taker pairing per leg.'
      type: object
      properties:
        id:
          type: string
          description: Fill ID.
        matchId:
          type: string
          description: One maker/taker pairing.
        orderId:
          type: string
          description: The order you signed.
        tradeId:
          type: string
          description: The bet this fill belongs to. The one filter key on this route.
        marketHash:
          type: string
          description: >-
            For a quarter-line bet this is the leg hash; for a parlay it is the
            parlay parent.
        quarterlineMarketHash:
          type: string
          nullable: true
          description: The quarter-line parent, or null for single-leg bets.
        isParlay:
          type: boolean
          description: True when this is a parlay row.
        userAddress:
          type: string
          description: Your user address — the proxy owner.
        wallet:
          type: string
          description: The proxy wallet the funds moved from.
        fillAmount:
          type: string
          description: Amount matched in this fill, in base units.
        fillOdds:
          type: string
          description: >-
            The price of this single match. The blended figure across a bet is
            `weightedAverageOdds` on the trade row.
        returnAmount:
          type: string
          description: >-
            Gross return from this match if it wins — stake included. Base
            units.
        netReturnAmount:
          type: string
          description: >-
            `returnAmount` less the settlement payout fee, projected from your
            live per-wallet rate.
        isBettingOutcomeOne:
          type: boolean
          description: Which outcome this row backs.
        isMaker:
          type: boolean
          description: >-
            Which side of the match this fill was on. `true` on the maker side —
            your order was the resting one; `false` on the taker side.
        status:
          type: string
          enum:
            - MATCHED
            - LOCKED
            - SETTLED
            - FAILED
          description: MATCHED, LOCKED, SETTLED or FAILED.
        txHash:
          type: string
          nullable: true
          description: The lock transaction. Null until the lock lands.
        ceRefundAmount:
          type: string
          description: In base units.
        ceRefundFeeAmount:
          type: string
          description: In base units.
        usedBetCredits:
          type: boolean
          description: Whether bet credits funded it.
        clientOrderId:
          type: string
          description: >-
            Your own tag from the order that produced this fill, present only
            when set.
        externalUserId:
          type: string
          description: >-
            Optional partner tag from the order that produced this fill, present
            only when set.
        createdAt:
          type: string
          description: ISO 8601. Fill insert time.
        updatedAt:
          type: string
          description: ISO 8601. Last mutation.
        settlement:
          allOf:
            - $ref: '#/components/schemas/FillV3Settlement'
          nullable: true
          description: Null until settled.
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
    FillV3Settlement:
      description: Note there is no `settleDate` at fill grain.
      type: object
      properties:
        outcome:
          type: integer
          description: 0 = void, 1 = outcome one won, 2 = outcome two won.
        settleReturnAmount:
          type: string
          description: Paid out at settlement for this fill, in base units.
        settleTxHash:
          type: string
          nullable: true
          description: The settlement transaction.
        settleFeeAmount:
          type: string
          nullable: true
          description: >-
            Fee taken at settlement, in base units, or null. Always a string at
            bet grain.
        settleCeRefundAmount:
          type: string
          description: Capital-efficiency refund snapshotted at settlement, in base units.
  securitySchemes:
    SxApiKey:
      type: apiKey
      in: header
      name: x-sx-api-key
      description: 'API key, sent as the `x-sx-api-key` header. '

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.