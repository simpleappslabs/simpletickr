# simpletickr

Domain glossary for simpletickr, a personal portfolio tracker (ETFs, crypto, stocks).

## Language

### Portfolio & holdings

**Portfolio**:
The aggregate root. Owns Transactions as child entities; any business rule spanning transactions belongs here.

**Transaction**:
A single buy/sell event recorded against a Portfolio and an Account.

**Holding**:
A computed read model of net position per asset, derived from Transactions. Not persisted — no DB table, no lifecycle.
_Avoid_: Position

**Asset**:
Reference data (ticker, exchange listings) that exists independently of any Portfolio. A separate aggregate from Portfolio.

**Account**:
A brokerage, bank, crypto, or retirement account that a Transaction is recorded against (`BROKERAGE`/`CRYPTO`/`BANK`/`RETIREMENT`/`OTHER`). Global, not scoped to a Portfolio or a User. Unrelated to authentication — see User/Identity below.

### Identity & access

**User**:
The account that owns application data — Portfolios, Accounts, dashboard widgets, settings. One row per person using the app.
_Avoid_: Account (see Account above — already means the financial account a Transaction is recorded against, not a login)

**Identity**:
A login method bound to a User via `userId` — either `LOCAL` (username + password hash) or `OIDC` (`providerId` + `subject`). A User can have multiple Identities; identity resolution for OIDC is strictly by `(providerId, subject)`, never by email.

**Organization** / **Membership**:
Schema exists (each User gets a personal Organization + OWNER Membership on creation, mirroring the simplebookmarks-go pattern) but is currently unused scaffolding — there is no instance-wide admin concept, and `Membership.role` (OWNER/ADMIN/MEMBER) is not read anywhere yet. Don't infer admin/multi-tenant behavior from these tables as they stand today.
