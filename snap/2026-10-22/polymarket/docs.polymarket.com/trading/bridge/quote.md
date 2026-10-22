> ## Documentation Index
> Fetch the complete documentation index at: https://docs.polymarket.com/llms.txt
> Use this file to discover all available pages before exploring further.

# Quote

> Preview fees and estimated output for deposits and withdrawals

Get an estimated quote before executing a deposit or withdrawal. Quotes include estimated output amounts, checkout time, and a detailed fee breakdown.

## Get a Quote

```bash theme={null}
curl -X POST https://bridge.polymarket.com/quote \
  -H "Content-Type: application/json" \
  -d '{
    "fromAmountBaseUnit": "10000000",
    "fromChainId": "137",
    "fromTokenAddress": "0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359",
    "recipientAddress": "0x17eC161f126e82A8ba337f4022d574DBEaFef575",
    "toChainId": "137",
    "toTokenAddress": "0xC011a7E12a19f7B1f670d46F03B03f3342E82DFB"
  }'
```

### Request Parameters

| Parameter | Type | Description |
| - | - | - |
| `fromAmountBaseUnit` | string | Amount to send in base units (e.g., `"10000000"` for 10 USDC) |
| `fromChainId` | string | Source chain ID (e.g., `"137"` for Polygon) |
| `fromTokenAddress` | string | Token contract address on the source chain |
| `recipientAddress` | string | Destination wallet address to receive funds |
| `toChainId` | string | Destination chain ID |
| `toTokenAddress` | string | Token contract address on the destination chain |

### Response

The quote response includes an estimated output amount, a fee breakdown, and a `quoteId` you can log for your own records. To track the transfer itself once it's underway, poll [`/status`](/trading/bridge/status) with your bridge address.

```json theme={null}
{
  "estCheckoutTimeMs": 45000,
  "estInputUsd": 10,
  "estOutputUsd": 9.94,
  "estToTokenBaseUnit": "9940000",
  "quoteId": "0x00c34ba467184b0146406d62b0e60aaa24ed52460bd456222b6155a0d9de0ad5",
  "estFeeBreakdown": {
    "gasUsd": 0.02,
    "appFeeLabel": "Bridge fee",
    "appFeePercent": 0.3,
    "appFeeUsd": 0.03,
    "fillCostPercent": 0.1,
    "fillCostUsd": 0.01,
    "maxSlippage": 0.5,
    "minReceived": 9.89,
    "swapImpact": 0.05,
    "swapImpactUsd": 0.005,
    "totalImpact": 0.6,
    "totalImpactUsd": 0.06
  }
}
```

| Field | Type | Description |
| - | - | - |
| `estCheckoutTimeMs` | number | Estimated checkout time in milliseconds |
| `estInputUsd` | number | Estimated input value in USD |
| `estOutputUsd` | number | Estimated output value in USD |
| `estToTokenBaseUnit` | string | Estimated output amount in base units |
| `quoteId` | string | Unique identifier for this quote |
| `estFeeBreakdown` | object | Detailed fee breakdown (see below) |

### Fee Breakdown

The `estFeeBreakdown` object contains:

**gasUsd** `number`

Gas fee in USD

**appFeeLabel** `string`

Label of the app fee

**appFeePercent** `number`

App fee as a percentage of the total amount

**appFeeUsd** `number`

App fee in USD

**fillCostPercent** `number`

Fill cost as a percentage of the total amount

**fillCostUsd** `number`

Fill cost in USD

**maxSlippage** `number`

Maximum potential slippage as a percentage

**minReceived** `number`

Minimum amount received after slippage

**swapImpact** `number`

Swap impact as a percentage of the total amount

**swapImpactUsd** `number`

Swap impact in USD

**totalImpact** `number`

Total impact as a percentage of the total amount

**totalImpactUsd** `number`

Total impact cost in USD

> [!NOTE]
>
> Quotes are estimates. Actual amounts may vary slightly due to market
> conditions.

## Next Steps

> **[Create Deposit](/trading/bridge/deposit.md)**
>
> Execute a deposit to Polymarket.

> **[Withdraw](/trading/bridge/withdraw.md)**
>
> Withdraw from Polymarket to another chain.


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.