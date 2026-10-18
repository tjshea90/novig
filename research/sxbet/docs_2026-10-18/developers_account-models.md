> ## Documentation Index
> Fetch the complete documentation index at: https://docs.sx.bet/llms.txt
> Use this file to discover all available pages before exploring further.

# Account models for external sites

> The two supported ways to represent your site's users on SX.bet.

If you are integrating SX.bet markets into your own site or app, there are two supported account
models for representing your users. Pick the one that matches how you want to manage accounts and
funds.

<Note>
  This page is about representing **your site's users**. If you are trading your own account, see
  [Posting orders](/developers/posting-orders) and [Market making](/developers/market-making)
  instead.
</Note>

## Single account

<Tip>
  The simplest path, and the recommended one for most integrations.
</Tip>

You create one SX.bet account, generate one [API key](/api-reference/api-key), and make every API
call from your backend with that key. Your single account represents all of your users.

Tell your users' activity apart with [`externalUserId`](/developers/external-user-id): set it to
your own id for the person on each order, and the same value comes back on their orders, fills, and
bets so you can attribute activity per user.

You choose how funds are held: custody all user funds yourself, or delegate custody to SX.

See [External user id](/developers/external-user-id) for how per-user attribution works.

## Whitelabel / skin

Each user on your app is their own SX.bet user, and your site is effectively a skin of
[sx.bet](https://sx.bet). You implement authentication directly from your own domain.

<Note>
  More information on this model is coming shortly. Reach out through
  [Work with us](/developers/work-with-us) if you are planning a whitelabel integration.
</Note>

## Related

<CardGroup cols={2}>
  <Card title="External user id" icon="users" href="/developers/external-user-id">
    Attribute orders, fills, and bets to people on your platform.
  </Card>

  <Card title="API keys" icon="lock" href="/api-reference/api-key">
    Generate and use the key your backend calls with.
  </Card>

  <Card title="Work with us" icon="envelope" href="/developers/work-with-us">
    Building at scale or exploring a partnership.
  </Card>
</CardGroup>


This documentation is built and hosted on [Mintlify](https://mintlify.com), a developer documentation platform.