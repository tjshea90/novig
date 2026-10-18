> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Exchange metadata

> Retrieve exchange metadata including contract addresses, supported tokens, and limits

<Note>
  This is a sample response. Visit [https://api.sx.bet/metadata/obv3](https://api.sx.bet/metadata/obv3) (or
  [https://api.toronto.sx.bet/metadata/obv3](https://api.toronto.sx.bet/metadata/obv3)) for up-to-date values.
</Note>


## OpenAPI

````yaml GET /metadata/obv3
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
  /metadata/obv3:
    get:
      tags:
        - Connection
      summary: Get OBv3 metadata
      description: >-
        The bootstrap document, and the first call any client should make. No
        authentication.


        **Read every value at runtime — do not copy the examples.** `chainId`,
        `activeAsset`, `transferToProxyExecutorAddress`, `bettingDelay` and the
        order-size minimum are per-environment. Chain and asset values for both
        networks are tabled on [Testnet &
        Mainnet](/developers/testnet-and-mainnet).


        **This is also the canonical order-surface availability probe**: 200
        means the surface is live, 503 means it is unavailable on that
        environment, and 404 means the deployment predates it entirely. Neither
        `GET /orders-v3/odds/best` nor a 401 tells you this.
      operationId: getMetadataObv3
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
                    $ref: '#/components/schemas/MetadataObv3'
              example:
                status: success
                data:
                  chainId: 79479957
                  domain:
                    name: OBv3 Escrow
                    version: '1'
                    chainId: 79479957
                    verifyingContract: '0x0Db3aBA7de0BCb41FF99D35306f366cFFEEede38'
                  activeAsset:
                    symbol: USDC
                    baseToken: '0x1BC6326EA6aF2aB8E4b6Bc83418044B1923b2956'
                    escrowAddress: '0x0Db3aBA7de0BCb41FF99D35306f366cFFEEede38'
                    decimals: 6
                  transferToProxyExecutorAddress: '0xBB2828D990eC30B46715773b654eC4D44703177d'
                  oddsLadderStepSize: 125
                  limits:
                    orderSizeMinimum: '1'
                    orderSizeMinimumBaseUnits: '1000000'
                    minRestingOrderSize: '0.1'
                    minRestingOrderSizeBaseUnits: '100000'
                    maxCreateOrders: 10
                    maxCancelOrders: 100
                  bettingDelay:
                    liveMsDefault: 8000
                    pregameMsDefault: 0
                    sportIdToMs: {}
                    leagueIdToMs: {}
        '404':
          description: ''
        '500':
          description: ''
        '503':
          description: ''
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorNest'
              example:
                message: Service Unavailable
                statusCode: 503
      security: []
components:
  schemas:
    MetadataObv3:
      description: >-
        Runtime configuration for the exchange. Read every value at runtime — do
        not copy the examples. Only `domain.name` and `domain.version` are
        fixed; `chainId`, `domain.verifyingContract`, `activeAsset`,
        `transferToProxyExecutorAddress`, `bettingDelay`, and the order-size
        minimum are per-environment.
      type: object
      properties:
        chainId:
          type: integer
          description: >-
            Chain id, used as the EIP-712 domain `chainId`. Differs per
            environment — see [Environments](/developers/environments).
          example: 79479957
        domain:
          description: >-
            Complete EIP-712 domain. Pass this object straight to
            `signTypedData`. `name` and `version` are fixed; `chainId` and
            `verifyingContract` are per-environment.
          type: object
          properties:
            name:
              type: string
              description: EIP-712 domain name. Always "OBv3 Escrow".
              example: OBv3 Escrow
            version:
              type: string
              description: EIP-712 domain version. The string "1", not "1.0".
              example: '1'
            chainId:
              type: integer
              description: EIP-712 domain chain id. Same value as the top-level `chainId`.
              example: 79479957
            verifyingContract:
              type: string
              description: >-
                EIP-712 verifying contract — the Escrow proxy. Same value as
                `activeAsset.escrowAddress`.
              example: '0x0Db3aBA7de0BCb41FF99D35306f366cFFEEede38'
        activeAsset:
          description: >-
            The single active (baseToken, escrowAddress) pair for this
            environment
          type: object
          properties:
            symbol:
              type: string
              example: USDC
              description: Settlement token symbol.
            baseToken:
              type: string
              description: >-
                ERC-20 address of the settlement token. Pass this as `baseToken`
                when creating orders.
            escrowAddress:
              type: string
              description: >-
                Escrow contract. Equals `domain.verifyingContract` — see
                [EIP-712 order signing](/api-reference/eip712-order-signing).
            decimals:
              type: integer
              example: 6
              description: >-
                Token decimals — 6 for USDC. Every amount in the API is in base
                units.
        transferToProxyExecutorAddress:
          type: string
          description: >-
            Spender required on EIP-2612 permits when transferring tokens to a
            proxy via `POST /user/transfer-to-proxy`. A mismatch returns
            `ERC20_PERMIT_BAD_SPENDER`.
          example: '0xBB2828D990eC30B46715773b654eC4D44703177d'
        oddsLadderStepSize:
          type: integer
          description: Odds must be a multiple of this x 1e15. 125 means 0.125%.
          example: 125
        limits:
          type: object
          properties:
            orderSizeMinimum:
              type: string
              description: Minimum order size in whole tokens.
              example: '1'
            orderSizeMinimumBaseUnits:
              type: string
              description: >-
                The same minimum in base units. Compare `totalBetSize` against
                this one, not against `orderSizeMinimum`.
              example: '1000000'
            minRestingOrderSize:
              type: string
              description: >-
                Minimum resting remainder in whole tokens. A partial fill that
                leaves less than this amount closes the order as filled
                (`inactiveReason` `FILLED`). Not the same as `orderSizeMinimum`.
              example: '0.1'
            minRestingOrderSizeBaseUnits:
              type: string
              description: >-
                The same minimum resting remainder in base units. A partial fill
                that leaves less than this amount is discarded and the order
                closes as filled (`inactiveReason` `FILLED`). Not the same as
                `orderSizeMinimumBaseUnits`.
              example: '100000'
            maxCreateOrders:
              type: integer
              example: 10
              description: Maximum orders in one `POST /orders-v3`.
            maxCancelOrders:
              type: integer
              example: 100
              description: Maximum order ids in one cancel request.
          description: Batch and size limits for this environment.
        bettingDelay:
          type: object
          description: >-
            Per-order delay in milliseconds before an order that takes resting
            liquidity becomes eligible to match. An order that only adds
            liquidity is unaffected and rests immediately. Live resolution:
            `leagueIdToMs[leagueId]`, then `sportIdToMs[sportId]`, then
            `liveMsDefault`. Pregame uses `pregameMsDefault` only. See [Betting
            delays](/developers/betting-delays).
          properties:
            liveMsDefault:
              type: integer
              description: >-
                Live-market delay in milliseconds when no league or sport
                override applies.
              example: 8000
            pregameMsDefault:
              type: integer
              description: Pregame delay in milliseconds. No league or sport override.
              example: 0
            sportIdToMs:
              type: object
              additionalProperties:
                type: number
              description: >-
                Per-sport live delay overrides in milliseconds. A matching
                league override wins.
              example: {}
            leagueIdToMs:
              type: object
              additionalProperties:
                type: number
              description: >-
                Per-league live delay overrides in milliseconds. Wins over the
                sport override and `liveMsDefault`.
              example: {}
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

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.