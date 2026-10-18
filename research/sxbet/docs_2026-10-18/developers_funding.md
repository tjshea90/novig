> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Funding

> How to fund your proxy wallet with USDC on SX Bet.

This page covers funding your proxy from **USDC you already hold in your EOA**. You can also skip the API and use the **deposit modal** on [sx.bet](https://sx.bet).

<Steps>
  <Step title="Read the active asset">
    [`GET /metadata/obv3`](/api-reference/get-metadata-obv3) gives you the token to fund with and the permit `spender`.
  </Step>

  <Step title="Sign a permit">
    An EIP-2612 signature over `(owner, spender, value, nonce, deadline)`.
  </Step>

  <Step title="Submit it">
    [`POST /user/transfer-to-proxy`](/api-reference/post-user-transfer-to-proxy). Returns `200` with a
    `sessionId`.
  </Step>

  <Step title="Wait, then verify">
    Poll
    [`GET /user/transfer-to-proxy/status`](/api-reference/get-user-transfer-to-proxy-status)
    with the returned `sessionId` until `status` is `SUCCESS` or `FAILED`, then confirm the money in
    [`/user/balance-v3`](/api-reference/get-user-balance-v3).
  </Step>
</Steps>

## Working code

```js theme={null}
const BASE = "https://api.sx.bet"; // Mainnet — use https://api.toronto.sx.bet for testnet
const API_KEY = process.env.SX_API_KEY;

const headers = { "x-sx-api-key": API_KEY };
const get = (path) =>
  fetch(`${BASE}${path}`, { headers }).then((r) => r.json());
const post = (path, body) =>
  fetch(`${BASE}${path}`, {
    method: "POST",
    headers: { ...headers, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  }).then((r) => r.json());

// 1. What are we funding with?
const { data: meta } = await get("/metadata/obv3");
const token = meta.activeAsset.baseToken;

// 2. Sign the permit. Give it a generous deadline — the floor is one hour.
const deadline = String(Math.floor(Date.now() / 1000) + 6 * 3600);
const signature = await signPermit({
  owner: myEoa,
  spender: meta.transferToProxyExecutorAddress,   // not your proxy
  tokenAddress: token,
  value: "500000000",                    // 500 USDC, base units
  deadline,
});

// 3. Submit.
const { data } = await post("/user/transfer-to-proxy", {
  owner: myEoa,
  spender: meta.transferToProxyExecutorAddress,
  tokenAddress: token,
  value: "500000000",
  deadline,
  signature,
});
console.log("queued:", data.sessionId, "→", data.proxyAddress);

// 4. Wait for this deposit's outcome, then confirm.
await waitUntil(async () => {
  const { data: st } = await get(
    `/user/transfer-to-proxy/status?sessionId=${data.sessionId}`
  );
  return st.status === "SUCCESS" || st.status === "FAILED";
});
const { data: bal } = await get("/user/balance-v3");
```

## Other ways to fund

The permit path is `FROM_EOA` as appears on your ledger endpoint. Two other deposit types appear in the ledger:

| `depositType` | Source |
| - | - |
| `FROM_EOA` | This flow — a permit-authorised transfer from your own wallet |
| `BRIDGE` | Funds arriving from another chain, using the deposit flow on sx.bet |
| `PROXY_TO_PROXY` | An internal transfer from another proxy |

## Related

<CardGroup cols={2}>
  <Card title="Fund a proxy wallet" icon="arrow-right-to-bracket" href="/api-reference/post-user-transfer-to-proxy">
    The request, the two warnings, and the `200`.
  </Card>

  <Card title="Reading balances" icon="scale-balanced" href="/developers/balances-and-ledger">
    Reading what actually arrived.
  </Card>

  <Card title="Accounts" icon="wallet" href="/developers/accounts">
    Deploy the proxy first, and poll until it is live.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.