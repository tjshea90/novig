> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# API Keys

> Generating and using an API key

An API key authenticates every request that is not public market data — orders, cancels, balances,
trades, fills, positions, and funding. It is also required to fetch a realtime connection token and
to register or cancel a heartbeat. Public market-data routes work without one. See
[Authentication by route](/api-reference/auth-matrix) for the full list of authenticated vs unauthenticated routes

## Generating API Key

1. Visit [sx.bet](https://sx.bet) and register or log in to your account. You can connect your MetaMask
   wallet, or log in using your Fortmatic email address.
2. If using MetaMask, `sign` the Signature Request.
3. Click the `Account` tab on the top navigation bar.
4. Click the `Overview` tab on the account navigation bar.
5. You will see an `API Credentials` card. Click `GENERATE API KEY NOW`. An API Key will be displayed.
6. The API Key generated will not be displayed again, so please **copy and save this key for future use**.

<Note>
  If you lose your key, you can generate a new one by following the same steps. Any previous keys used
  will be unauthorized if you generate a new key. Only one key can be active at a time.
</Note>

For Toronto testnet, generate the key on [toronto.sx.bet](https://toronto.sx.bet) — keys are
per-environment. See [Environments](/developers/environments).

## Usage

```bash theme={null}
curl --location --request GET 'https://api.sx.bet/user/realtime-token-v3/api-key' \
  --header 'x-sx-api-key: XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX'
```

Pass your API key in the `x-sx-api-key` header. The
example above fetches a realtime token used to authenticate WebSocket connections. See
[Initialization](/developers/realtime-initialization#connect).

Once your API key is generated, add it as an HTTP header named `x-sx-api-key` on every authenticated
request.

<Warning>
  **`x-api-key` is a different, legacy credential and the wrong header here.**
</Warning>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.