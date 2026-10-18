> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Accounts

> Understand accounts and proxy wallets on SX Bet.

An account is two addresses. You **sign** with one and you are **funded** at the other.

| | |
| - | - |
| **Your EOA** | The wallet you control with a private key. Your identity to the API |
| **Your proxy wallet** | A contract your EOA owns. Where your money lives and where bets settle |

## Creating an account

Sign up at [sx.bet](https://sx.bet) and choose a username. Two ways in:

* **Email or Google** — a non-custodial [Magic](https://magic.link/) wallet is created for you, tied
  to your email. Easiest with no prior crypto experience.
* **Connect a wallet** — bring an existing wallet such as [MetaMask](https://metamask.io) or
  [Rabby](https://rabby.io).

You end up with a public address and a private key. A proxy wallet is deployed for you when you log in to the site, or when you call [`POST /user/deploy-proxy`](/api-reference/post-user-deploy-proxy).

## Non-custodial

USDC sits in a proxy contract your account. **Trading is proxy-only.** Funds must be in your proxy wallet to trade. Withdrawals and transfers out of your proxy wallet require your signature.

## Your address and private key

* Your **address** is your public identity — something like
  `0x52adf738AAD93c31f798a30b2C74D658e1E9a562`. It is how the API identifies you and it is safe to
  share.
* Your **private key** proves you own the wallet. It signs every order. Never expose this.

<Warning>
  Anyone with your private key has full control of your wallet and funds. Never share it, never hardcode it, never commit it — use environment variables.
</Warning>

If you signed up with email or Google, you can retrieve your private key from the
[assets page](https://sx.bet/wallet/assets):

## Looking up your proxy

[`GET /user/proxy`](/api-reference/get-user-proxy) returns your proxy address.

<Note>
  To see deposits, withdrawals, and settlements on your account, call
  [`GET /ledger-v3`](/api-reference/get-ledger-v3).
</Note>

## The Safe underneath

Every deployed proxy has a Safe smart account automatically attached, reported as `multisigSafeAddress` on [`GET /user/proxy`](/api-reference/get-user-proxy). The Safe authorises withdrawal, transfers to another proxy, and signer changes. Read its state with [`GET /user/multisig/{safeAddress}/info`](/api-reference/get-user-multisig-info), and start a withdrawal with [`POST /user/multisig/withdraw/prepare`](/api-reference/post-user-multisig-withdraw-prepare).

<sub>Safe and Safe\{Core} are trademarks of the Safe Ecosystem Foundation, used here descriptively.
SX Bet is not affiliated with or endorsed by Safe Global.</sub>

## Related

<CardGroup cols={2}>
  <Card title="Deploy a proxy wallet" icon="rocket" href="/api-reference/post-user-deploy-proxy">
    The request, what a repeat call does, and the `200`.
  </Card>

  <Card title="Look up a proxy wallet" icon="magnifying-glass" href="/api-reference/get-user-proxy">
    The predicted address, the `deployed` flag, and the Safe address.
  </Card>

  <Card title="Get your ledger" icon="book" href="/api-reference/get-ledger-v3">
    Every deposit, withdrawal, and settlement on your account.
  </Card>

  <Card title="Funding" icon="arrow-right-to-bracket" href="/developers/funding">
    Moving USDC in with a permit, without paying gas.
  </Card>

  <Card title="Reading balances" icon="scale-balanced" href="/developers/balances-and-ledger">
    What you can actually spend, and why.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.