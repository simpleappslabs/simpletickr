package com.simpletickr.auth.oidc

/**
 * Set by OidcConnectController before redirecting to the IdP, read (and cleared) by the
 * success/failure handlers on callback. Stashing intent server-side in the session — rather than
 * trusting a client-supplied query param or registration id — is what makes "this callback is a
 * connect, not a fresh login" trustworthy.
 */
const val OIDC_CONNECT_SESSION_ATTRIBUTE = "oidc.connect.userId"
