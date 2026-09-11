# OIDC client: provisioning, principal shape, and session-scoped tokens

Issue #57 (Phase 2: OIDC client support) makes simpletickr an OIDC *client* against a single
self-hoster-chosen IdP, additive alongside the local auth shipped in Phase 1 (#56). This records
the decisions in that design that aren't obvious from the issue text alone.

**Auto-provisioning, not admin-mediated linking.** Per ADR-0002, OIDC is *the* mechanism by which
simpletickr gets multi-user support at all — there is no local admin tooling to create a second
user. So a login from a `(provider_id, subject)` never seen before must create a new `User`
automatically, with the IdP itself as the trust boundary (if it authenticates someone, simpletickr
trusts that) rather than simpletickr owning any pre-approval or allowlist step. "Connect an
identity" (linking OIDC to an existing local account) is a separate, self-service, one-directional
action — a local account gains SSO — not the primary way users come to exist.

**`Principal` becomes a sealed type.** Today's `CurrentUser` bakes in `passwordHash` and
implements Spring's `UserDetails` directly — it's LOCAL-shaped. An OIDC-authenticated request has
no password hash. Rather than making `passwordHash` nullable (the nullable-abuse this codebase
otherwise avoids), `Principal` becomes sealed (`Principal.Local` / `Principal.Oidc`), keeping the
Spring Security `UserDetails` adapter separate from the domain-facing principal shape.

**Spring's `oauth2Login()`, not a hand-rolled flow.** simplebookmarks-go's Phase 2 plan reaches for
`go-oidc`+`oauth2` directly because Go has no framework-integrated equivalent. Kotlin/Spring does:
`spring-boot-starter-oauth2-client` already implements discovery, Authorization Code + PKCE, and
ID-token validation (signature/issuer/audience/exp/nonce) as maintained, well-audited code. Phase 1
already committed this codebase to Spring Security; hand-rolling the same flow here would duplicate
security-critical code the framework already provides, for no described benefit.

**Login and connect share one OAuth dance, disambiguated server-side.** Both actions drive the
same Authorization Code redirect. Two distinct start endpoints correlate to intent (plus the
current user id, for connect) via server-tracked state — never a client-supplied flag, which would
let a fresh login attempt claim to be linking to an arbitrary existing account. A connect callback
whose `(provider_id, subject)` already resolves to a *different* user is rejected outright,
consistent with ADR-0002's "never auto-link" rule.

**Tokens are retained and refreshed lazily, not discarded after login.** Discarding OIDC tokens
once the session is established is simpler, but means a user revoked at the IdP stays logged into
simpletickr until their local session happens to expire on its own — there's no way to notice.
Retaining the refresh token and refreshing it lazily (on the first request after the access/ID
token would be stale; no scheduled sweep) means a failed refresh surfaces revocation directly. The
refresh token piggybacks on the existing Spring Session JDBC row already used for the security
context — no new table — accepting a known gap: that table isn't encrypted at rest today. That
gap is recorded here, not solved as part of #57.

**OIDC-only accounts have no local break-glass fallback, and that's accepted.** An
auto-provisioned user has no LOCAL identity; if the IdP is ever unreachable or misconfigured, they
have no other way to log in. We considered letting such a user self-service-set a local password
(symmetric to "connect"), but decided against it here: the bootstrap admin already has guaranteed
local access, and OIDC-only lockout is treated as an accepted property of choosing OIDC
self-hosting, not a gap simpletickr needs to close. (Disconnecting an OIDC identity — the reverse
of "connect" — is tracked separately as #69, and wouldn't close this gap anyway, since it doesn't
apply to identities with no LOCAL fallback in the first place.)

**Everything else defaults to graceful degradation, not a hard requirement.** A misconfigured or
unreachable OIDC provider at startup does not stop the app from starting — local login is the
break-glass path specifically so a bad OIDC config can't brick a self-hosted instance.
RP-initiated logout (also signing the user out at the IdP) is an instance-level config toggle,
defaulting off, rather than fixed behavior or a per-logout user choice — a self-hoster sharing one
IdP across several apps shouldn't have simpletickr silently log them out of everything else by
default.
