# simpletickr

Domain glossary for simpletickr, a personal portfolio tracker (ETFs, crypto, stocks).

## Language

### Portfolio & transactions

**Portfolio**:
The aggregate root. Owns Transactions and Transfers as child entities; any business rule spanning them belongs here.

**Transaction**:
A `BUY`, `SELL`, or `SPLIT` event for one Listing, recorded against a Portfolio and an Account. Carries price, quantity, fees, and (when the Listing's currency differs from the portfolio's base currency) the FX rate at execution time.
_Avoid_: Trade (a Transaction is one row; see CryptoTrade below for the user-facing "trade" concept, which is a pair of Transactions)

**Transfer**:
Moves custody of a quantity of one Listing from one Account to another within the same Portfolio. Has no price and no cost basis — only its optional fee (paid in the asset itself, e.g. a network fee) affects portfolio inventory; cost basis is always derived by replaying Transactions, never frozen on the Transfer.
_Avoid_: Transaction (a Transfer never carries a price and is never a BUY/SELL)

**CryptoTrade**:
The user-facing concept of swapping one crypto asset for another (e.g. BTC→ETH) in a single action. Recorded as two ordinary Transactions (one SELL, one BUY) sharing a `tradeId`; deleting either leg cascades to delete both. Holdings/valuation/gains logic needs no CryptoTrade-specific handling — it already processes BUY/SELL by sign.

**Asset**:
Reference data (name, ISIN, type) that exists independently of any Portfolio — a separate aggregate.

**Listing**:
A specific exchange quotation of an Asset (ticker + exchange + currency). One Asset can have multiple Listings (e.g. cross-listed on two exchanges); Transactions and Transfers reference a Listing, not an Asset directly.

**Account**:
A brokerage, bank, crypto, or retirement account that a Transaction or Transfer is recorded against (`BROKERAGE`/`CRYPTO`/`BANK`/`RETIREMENT`/`OTHER`). Global, not scoped to a Portfolio. Unrelated to authentication — see User/Identity below.

> **Naming collision — "broker":** `Account.broker` is a free-text label the user can set on an Account (e.g. "Interactive Brokers"), independent of `accountType`/`institution`. Separately, `AssetImportMapping.broker` is an internal key identifying which broker-specific parser produced an imported row (currently only `"bolero"`). These are unrelated concepts that happen to share a field name — don't assume one implies the other.

### Positions & valuation

**Holding**:
Pure weighted-average-cost aggregation of Transactions/Transfers into a net position, one row per (Asset, Listing). No valuation, no FX — quantity and cost basis only, in the Listing's own currency.
_Avoid_: Position

**HoldingWithValuation**:
A Holding overlaid with current market value and unrealized P&L, converted to the portfolio's base currency via price + FX lookups. All valuation fields are nullable — a Holding always exists, valuation only when price/FX data is available.

**AssetHolding**:
Rolls up every Listing of the same Asset (e.g. cross-listed on two exchanges) into one Asset-level row for display.

**AccountHolding** / **AccountValuation**:
The Account-scoped counterpart to Holding: quantity (and separately, market value) held per (Account, Listing). No cost basis — Transfers carry no price, so cost can't be attributed to a specific Account without an explicit lot-transfer policy.

**PortfolioValuationSummary**:
Portfolio-level rollup of AssetHolding valuations, explicit about what it excluded (count + names) rather than leaving a consumer to guess why a total came back null.

### Realized gains

**RealizedGainsReport**:
The result of closing out (selling) positions over a date range under a chosen RealizationMethod — a list of RealizedGainEntry plus per-currency totals. Per-currency totals are safe to display independently; never sum across currencies (unconverted).

**RealizationMethod**:
`FIFO` or `AVERAGE_COST` — the two supported cost-basis methods for computing realized gains.
_Avoid_: AVCO (not used in code; historical typo in old docs)

**RealizedGainLot**:
For FIFO only: which specific acquisition lot (or share of one) contributed to a RealizedGainEntry. Average cost has no discrete lots — a single blended pool per asset — so this is always empty for AVERAGE_COST entries.

### Pricing & FX

**PricePoint**:
A Listing's closing price on a given date, sourced from a price provider (e.g. Yahoo Finance) via a PriceProviderMapping.

**FxRate**:
An exchange rate between two currencies on a given date. Sourced `AUTO` (fetched), `USER` (manually entered), or `IMPORTED` (came in via a data import).

### Import & export

**SimpletickrExport**:
The portable backup/migration format for a user's data — one JSON file, versioned (`schemaVersion`).

**Import plan**:
The result of reconciling a SimpletickrExport against a user's existing data before applying it — matches exported entities to existing ones (by UUID, then ISIN/listing key), flags ambiguous matches, and identifies which Transactions/Transfers are already-imported duplicates by natural key. Computed once, pure (no repository calls), then either previewed (dry run) or persisted.

**Broker import**:
Parsing a specific broker's own transaction export (e.g. Bolero's CSV) into simpletickr Transactions, distinct from SimpletickrExport/import above, which round-trips simpletickr's own format. Uses AssetImportMapping to resolve a broker's external asset names to simpletickr Assets.

### Identity & access

**User**:
The account that owns application data — Portfolios, Accounts, dashboard widgets, settings. One row per person using the app.
_Avoid_: Account (see Account above — already means the financial account a Transaction is recorded against, not a login)

**Identity**:
A login method bound to a User via `userId` — either `LOCAL` (username + password hash) or `OIDC` (`providerId` + `subject`). A User can have multiple Identities; identity resolution for OIDC is strictly by `(providerId, subject)`, never by email.

**Organization** / **Membership**:
Schema exists (each User gets a personal Organization + OWNER Membership on creation, mirroring the simplebookmarks-go pattern) but is currently unused scaffolding — there is no instance-wide admin concept, and `Membership.role` (OWNER/ADMIN/MEMBER) is not read anywhere yet. Don't infer admin/multi-tenant behavior from these tables as they stand today.
