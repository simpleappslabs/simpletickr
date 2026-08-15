# No broker-style transaction invariants

simpletickr records portfolio transactions for analysis; it is not a broker and does not gatekeep what transactions are allowed to happen. We deliberately do **not** enforce "can't sell more than you hold" or similar cross-transaction invariants as hard validation — a transaction is recorded as reported, even if it implies a negative or otherwise implausible position. The holdings-as-of-date check in `RecordTransferUseCase` and the `oversellWarning` in `TransactionForm.svelte` are advisory nudges toward a likely data-entry mistake, not enforced business rules, and should not be extended into hard validation across the rest of the transaction/transfer surface.

This came out of an architecture review that flagged `Portfolio` as a shallow "aggregate root" for lacking this invariant; the finding was rejected on the above grounds — `Portfolio` is correctly thin, not incomplete.
