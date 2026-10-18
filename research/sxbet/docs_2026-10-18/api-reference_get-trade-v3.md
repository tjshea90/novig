> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Get a trade by ID

> Look up one of your bets by its opaque tradeId.

`GET /trades-v3/{tradeId}` returns **one bet** (BET grain) belonging to the authenticated account —
the same row shape as [`GET /trades-v3`](/api-reference/get-trades-v3).

Use it to join fills via [`GET /fills-v3?tradeId=…`](/api-reference/get-fills-v3).

<Note>
  A missing id, or an id that belongs to another account, returns **404**. The response does not
  distinguish those cases.
</Note>


## OpenAPI

````yaml GET /trades-v3/{tradeId}
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
  /trades-v3/{tradeId}:
    get:
      tags:
        - Trades
      summary: Get a trade by ID
      description: >-
        Returns one bet (BET grain) belonging to the authenticated account, by
        its opaque `tradeId`. A missing or foreign id returns 404.
      operationId: getTradeV3ById
      parameters:
        - name: tradeId
          in: path
          required: true
          schema:
            type: string
          description: >-
            Opaque bet id. Same value as `tradeId` on trade rows; use it to
            filter `GET /fills-v3`.
          example: '0xac6bb30bba6ea82d8717219a4fa49d15b58fd638fba479f294b4d402376d14d6'
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
                      trade:
                        $ref: '#/components/schemas/TradeV3'
                    required:
                      - trade
              example:
                status: success
                data:
                  trade:
                    tradeId: >-
                      0xac6bb30bba6ea82d8717219a4fa49d15b58fd638fba479f294b4d402376d14d6
                    userAddress: '0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5'
                    wallet: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                    marketHash: >-
                      0x1fec4ed9b71c2f812db900af7d12446158a0eda56041106f99551c496efd3266
                    isBettingOutcomeOne: false
                    isMaker: false
                    isParlay: false
                    totalStake: '1000000'
                    totalReturn: '2500000'
                    netTotalReturn: '2488000'
                    weightedAverageOdds: '40000000000000000000'
                    ceRefundAmount: '0'
                    ceRefundFeeAmount: '0'
                    usedBetCredits: false
                    fillCount: 2
                    ownOrderIds:
                      - >-
                        0x02d22c2ac3f2aa27ca96e156aa2fa4affb15064a169e02b55881bdd6867c55ea
                    status: LOCKED
                    betTime: '2026-07-31T18:47:09.577Z'
                    gameTime: '2040-01-01T00:00:00.000Z'
                    createdAt: '2026-07-31T18:47:09.575Z'
                    updatedAt: '2026-07-31T18:47:12.837Z'
                    settlement: null
        '404':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: Trade not found
                error: Not Found
                statusCode: 404
        '503':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
      security:
        - SxApiKey: []
components:
  schemas:
    TradeV3:
      description: >-
        Bet grain: one row per bet. Carries `isParlay` but no quarter-line flag,
        so `/trades-v3` alone cannot distinguish a quarter-line bet from a
        single — drill into `/fills-v3`.
      type: object
      properties:
        tradeId:
          type: string
          description: Identifies the bet. Filter `GET /fills-v3` by this value.
        userAddress:
          type: string
          description: Your user address — the proxy owner.
        wallet:
          type: string
          description: >-
            The proxy wallet the funds moved from. A different address from
            `userAddress`.
        marketHash:
          type: string
          description: >-
            The market hash for this trade. For a quarter-line bet, this is the
            parent hash — leg hashes are on the fills. See [Quarter-line
            markets](/developers/quarter-line-markets) for how the parent and
            leg hashes relate.
        isBettingOutcomeOne:
          type: boolean
          description: Which outcome you backed.
        isMaker:
          type: boolean
          description: >-
            Which side of the book you were on. `true` when you were the maker —
            your order rested and was filled; `false` when you were the taker.
        isParlay:
          type: boolean
          description: True when `marketHash` is a parlay parent.
        totalStake:
          type: string
          description: What you risked, in base units.
        totalReturn:
          type: string
          description: >-
            Gross return if this bet wins, in base units, stake included. Profit
            is `totalReturn - totalStake`.
        netTotalReturn:
          type: string
          description: >-
            `totalReturn` less the settlement payout fee, projected from your
            per-wallet rate.
        weightedAverageOdds:
          type: string
          description: Implied odds. Stake-weighted mean of the fill odds.
        ceRefundAmount:
          type: string
          description: >-
            In base units. On a parlay row (`isParlay: true`), this is an early
            stake refund rather than a capital-efficiency refund — see [Your
            bets](/developers/which-grain#bets).
        ceRefundFeeAmount:
          type: string
          description: >-
            In base units. Same caveat as `ceRefundAmount` — see [Your
            bets](/developers/which-grain#bets).
        usedBetCredits:
          type: boolean
          description: Whether bet credits funded this bet.
        fillCount:
          type: integer
          description: How many `fill_v3` rows exist under this `tradeId`.
        ownOrderIds:
          type: array
          items:
            type: string
          description: >-
            Order ids of your own orders behind this bet — the same values as
            `id` on order rows and `orderId` from `POST /orders-v3`. These are
            not `clientOrderId`s.
        status:
          type: string
          enum:
            - MATCHED
            - LOCKED
            - SETTLED
            - FAILED
          description: >-
            See [Bet lifecycle](/developers/bet-lifecycle) for what each status
            means.
        externalUserId:
          type: string
          description: >-
            Optional partner tag from the order that produced this bet, present
            only when set.
        betTime:
          type: string
          description: >-
            ISO 8601 timestamp of when the bet was placed. This route sorts by
            this field.
        gameTime:
          type: string
          description: ISO 8601. Scheduled start of the fixture.
        createdAt:
          type: string
          description: ISO formatted date string of when this trade record was created
        updatedAt:
          type: string
          description: ISO formatted date string of when this trade record was last updated
        settlement:
          allOf:
            - $ref: '#/components/schemas/TradeV3Settlement'
          nullable: true
          description: Null until the bet settles.
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
    TradeV3Settlement:
      type: object
      properties:
        outcome:
          type: integer
          description: >-
            0 = void (or a corrupt row — there is no enum guarding this), 1 =
            outcome one, 2 = outcome two.
        settleReturnAmount:
          type: string
          description: >-
            Paid out at settlement, as a base-unit decimal string (USDC has 6
            decimals).
        settleFeeAmount:
          type: string
          description: >-
            Fee taken at settlement, in base units. Always a string at bet
            grain; the same field on a fill may be null.
        settleCeRefundAmount:
          type: string
          description: Capital-efficiency refund snapshotted at settlement, in base units.
        settleDate:
          type: string
          description: ISO 8601. When settlement was recorded.
  securitySchemes:
    SxApiKey:
      type: apiKey
      in: header
      name: x-sx-api-key
      description: 'API key, sent as the `x-sx-api-key` header. '

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.