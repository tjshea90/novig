> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Fund a proxy wallet

> Transfer tokens from your wallet to your proxy wallet on SX Bet.

`POST /user/transfer-to-proxy` moves tokens from your EOA into your proxy wallet. You authorise it with
an **EIP-2612 permit** — a signature over `(owner, spender, value, nonce, deadline)`. [Funding](/developers/funding.md) walks the
whole deposit end to end.

> [!WARNING]
>
> A `200` means recorded, not completed. This is an asynchronous endpoint. Poll
> [`GET /user/transfer-to-proxy/status`](/api-reference/get-user-transfer-to-proxy-status)
> with the returned `sessionId` until `status` is `SUCCESS` or `FAILED`.


## OpenAPI

````yaml POST /user/transfer-to-proxy
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
  /user/transfer-to-proxy:
    post:
      tags:
        - Connection
      summary: Fund a proxy wallet
      description: >-
        Moves ERC-20 funds from your EOA into your proxy using an EIP-2612
        permit, so you pay no gas. A 200 means RECORDED, not settled — poll `GET
        /user/transfer-to-proxy/status?sessionId=` with the returned `sessionId`
        until `SUCCESS` or `FAILED`. Use `GET /user/transfer-to-proxy/pending`
        only to list open deposit `sessionIds`.
      operationId: transferToProxy
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - owner
                - spender
                - tokenAddress
                - value
                - deadline
                - signature
              properties:
                owner:
                  type: string
                  description: >-
                    Your EOA. Must equal the address you authenticated as, or
                    the request is a 401 OWNER_MISMATCH.
                spender:
                  type: string
                  description: >-
                    The environment's transfer-to-proxy executor — not the token
                    contract, and not your proxy. Read it from
                    `transferToProxyExecutorAddress` on [`GET
                    /metadata/obv3`](/api-reference/get-metadata-obv3). It is
                    part of the signed payload, so a wrong value returns
                    `ERC20_PERMIT_BAD_SPENDER` and has to be re-signed.
                tokenAddress:
                  type: string
                  description: The ERC-20 being moved.
                value:
                  type: string
                  description: Base units, as a positive big-number string.
                deadline:
                  type: string
                  description: >-
                    Unix seconds, as a string rather than a number. Must be at
                    least one hour in the future: a permit expiring in
                    fifty-nine minutes is refused even though it is valid
                    ERC-20, so sign with a margin of several hours.
                signature:
                  type: string
                  description: >-
                    The EIP-2612 permit signature, 65 bytes hex. Recovery is
                    checked last, so a `ERC20_PERMIT_SIGNATURE_MISMATCH` means
                    the deadline, spender and token were all accepted and the
                    fault is in the signing — a wrong nonce, a wrong token name
                    in the domain, or a chain-id mismatch.
                proxyAddress:
                  type: string
                  description: >-
                    Optional destination proxy, defaulting to your own. A named
                    proxy is checked only for being deployed and having an owner
                    — not for being yours — so a stale or copy-pasted value
                    sends your deposit to a proxy you cannot bet from, and the
                    call still returns 200. Omit it unless you mean it.
            example:
              owner: '0xbcc6D643e4159A75ED1dB4e13330230B82F2AEe5'
              spender: '0x0000000000000000000000000000000000000000'
              tokenAddress: '0x1BC6326EA6aF2aB8E4b6Bc83418044B1923b2956'
              value: '500000000'
              deadline: '1785527222'
              signature: '0x00'
      responses:
        '200':
          description: Recorded, not settled.
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
                        description: >-
                          The permit EIP-712 digest, hex without the 0x prefix.
                          It is how you match a pending deposit to the permit
                          you signed, and it is why the same permit can only
                          ever fund once, returning `TRANSFER_ALREADY_SUBMITTED`
                          on a resubmit.
                      proxyAddress:
                        type: string
                        description: >-
                          The resolved destination. Echo it back before treating
                          the deposit as done — it is your one chance to catch a
                          wrong or stale `proxyAddress` in the request.
                      value:
                        type: string
                        description: Base units being transferred.
              example:
                status: success
                data:
                  sessionId: >-
                    9c8f5b21e4d0a7c3f18b6e94d2a05c7318fa4b62d9e0173c5a8b2f4e6019d3c7
                  proxyAddress: '0x4361123dbdc1D812fdf7D27045aF358C9C8AA70A'
                  value: '500000000'
        '400':
          description: >-
            Validation failure. `message` is an array for field validators and a
            bare string otherwise — normalise both.
          content:
            application/json:
              schema:
                oneOf:
                  - $ref: '#/components/schemas/ErrorNestArray'
                  - $ref: '#/components/schemas/ErrorNest'
              example:
                message: TRANSFER_ALREADY_SUBMITTED
                error: Bad Request
                statusCode: 400
        '401':
          description: Missing or invalid credential.
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