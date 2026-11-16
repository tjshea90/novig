> ## Documentation Index
> Fetch the complete documentation index at: https://docs.novig.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Register a maker

> | | |
| --- | --- |
| **Key** | `trading` |
| **Throttle** | `account` |
| **Cost** | `1 /request` |
| **Answers** | <span class="st st-ok">200</span> <span class="st st-ok">201</span> <span class="st st-warn">401</span> <span class="st st-warn">403</span> <span class="st st-hold">423</span> <span class="st st-hold">429</span> <span class="st st-warn">451</span> |
| **Idempotent** | `true` |

Makes this key's subaccount an RFQ maker. Your maker quotes from the
subaccount's balance. A new maker is basic: it confirms each trade in a
last look and must confirm nearly all of them.
It can take up to a minute before every request sees the new maker.



## OpenAPI

````yaml /api-reference/spec-files/openapi-rfq-v3.json post /rfq/v3/maker
openapi: 3.1.0
info:
  title: Novig RFQ API
  description: >-
    Price parlays as an RFQ maker. Each maker is one subaccount, and you call
    these endpoints with that subaccount's trading key.
  contact:
    name: Contact
    url: https://novig.com
    email: tech@novig.com
  version: 3.0.0
servers:
  - url: https://api.paper.novig.com
    description: Paper
security: []
tags:
  - name: Maker
    description: Register a subaccount as a maker, and check its standing.
  - name: Executions
    description: See the parlays your maker filled and the collateral they hold.
  - name: Streaming
    description: Receive RFQs and quote, decline and confirm over one websocket.
paths:
  /rfq/v3/maker:
    post:
      tags:
        - Maker
      summary: Register a maker
      description: >-
        | | |

        | --- | --- |

        | **Key** | `trading` |

        | **Throttle** | `account` |

        | **Cost** | `1 /request` |

        | **Answers** | <span class="st st-ok">200</span> <span class="st
        st-ok">201</span> <span class="st st-warn">401</span> <span class="st
        st-warn">403</span> <span class="st st-hold">423</span> <span class="st
        st-hold">429</span> <span class="st st-warn">451</span> |

        | **Idempotent** | `true` |


        Makes this key's subaccount an RFQ maker. Your maker quotes from the

        subaccount's balance. A new maker is basic: it confirms each trade in a

        last look and must confirm nearly all of them.

        It can take up to a minute before every request sees the new maker.
      operationId: registerMaker
      responses:
        '200':
          description: Your subaccount was already a maker.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RegisterResponse'
        '201':
          description: Your subaccount is now a maker.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RegisterResponse'
        '401':
          description: >-
            The signature is absent or invalid, or the key is unknown, revoked,
            or expired.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorBody'
              example:
                code: SIGNATURE_REJECTED
                message: signature verification failed
        '403':
          description: >-
            The key's scope doesn't grant this route, or your account hasn't
            passed KYC or KYB (`KYC_REQUIRED`).
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RfqErrorBody'
        '423':
          description: >-
            Your account is locked out of trading, or you self-excluded
            (`SELF_EXCLUDED`).
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/RfqErrorBody'
        '429':
          description: >-
            The throttle is empty. Wait the number of seconds in `Retry-After`.
            Then retry.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorBody'
              example:
                code: RATE_LIMIT_EXCEEDED
                message: Rate limit exceeded. Please wait before retrying.
        '451':
          description: >-
            Geolocation refused the request: an anonymized network, a restricted
            region, or, for a placement, no device geolocation in the last 3
            days.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorBody'
              example:
                code: ANONYMIZED_NETWORK
                message: The request came over a VPN, a proxy, or a Tor exit.
      security:
        - keyId: []
          signature: []
          timestamp: []
components:
  schemas:
    RegisterResponse:
      type: object
      description: The maker on your subaccount. `GET /rfq/v3/maker` shows its details.
      required:
        - makerId
      properties:
        makerId:
          type: string
          format: uuid
    ErrorBody:
      type: object
      description: Every error on the surface carries this body.
      required:
        - code
        - message
      properties:
        code:
          type: string
          description: Stable identifier. The only field to branch on.
          example: MARKET_NOT_FOUND
        message:
          type: string
          description: Human-readable. Not stable. Never match on it.
        rejected:
          type:
            - array
            - 'null'
          items:
            $ref: '#/components/schemas/BatchOrderError'
          description: >-
            One entry per refused order in a batch. Present only on an error
            that a batch caused.
    RfqErrorBody:
      type: object
      description: The body of every error from an RFQ endpoint.
      required:
        - code
        - message
      properties:
        code:
          type: string
          description: A stable code for the error. Branch on this field.
          example: MAKER_NOT_FOUND
        message:
          type: string
          description: A readable explanation. It can change, so don't match on it.
    BatchOrderError:
      type: object
      description: >-
        One rejected order within a batch placement: which order failed, and
        why.
      required:
        - index
        - outcomeId
        - reason
      properties:
        index:
          type: integer
          description: Zero-based position in the request.
          minimum: 0
        outcomeId:
          type: string
          description: The outcome ID of the rejected order.
        reason:
          type: string
          description: >-
            Human-readable. Not stable. Branch on the envelope's `code` and on
            `index`.
  securitySchemes:
    keyId:
      type: apiKey
      in: header
      name: Novig-Key-Id
      description: The key's UUID.
    signature:
      type: apiKey
      in: header
      name: Novig-Signature
      description: Standard padded base64 of the NOVIG-V3 signature.
    timestamp:
      type: apiKey
      in: header
      name: Novig-Timestamp
      description: Unix milliseconds. ±30 s.

````

This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.