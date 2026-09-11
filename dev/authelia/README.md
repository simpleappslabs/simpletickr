# Dev-only Authelia IdP

A real OIDC provider, entirely config-as-code, for manually clicking through simpletickr's
OIDC login/connect flow (#57) in a browser — as opposed to the WireMock-stubbed IdP the
backend's `OidcAuthFlowIT` test uses, which proves the code works but isn't something you can
open in a browser.

**Never use anything in this directory outside local dev.** Every secret here (session
secret, storage encryption key, OIDC HMAC secret, the RSA signing key, the client secret, the
test user's password) is a fixed, checked-into-the-repo, throwaway value.

## Why Authelia needs TLS for this

Authelia refuses to start with an `authelia_url` that isn't `https://` — that's Authelia
protecting its own session cookie, unrelated to whether OIDC itself needs TLS. So this setup
terminates TLS directly in Authelia with a self-signed cert (`tls.crt`/`tls.key`, generated
once via `openssl`, checked in) rather than standing up a reverse proxy just for that.

That has one consequence: **the backend's own outbound calls** to Authelia (OIDC discovery,
token exchange, JWKS) will reject that self-signed cert unless the JVM is told to trust it.
`dev-truststore.p12` (password `changeit`) is a tiny truststore containing just that one cert
for exactly this purpose.

## Running it

```bash
task dev:oidc:backend
```

Starts Postgres and Authelia (if not already up) and the backend, with `OIDC_ISSUER_URI`,
`OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `FRONTEND_BASE_URL`, and the truststore
(`JAVA_TOOL_OPTIONS`) all set for you — see the `dev:oidc:backend` task in the root
`Taskfile.yml` for the exact values. It runs `./gradlew --no-daemon bootRun` specifically to
avoid the stale-daemon gotcha below.

And the frontend as usual: `task frontend:dev`.

Then open `http://localhost:5173/login` — "Continue with SSO" should appear. Your browser
will show a one-time self-signed-certificate warning for `127.0.0.1:9091` when you get
redirected there; click through it (it's the same cert the backend was just told to trust).

**Test user:** username `dev-sso`, password `devpassword`.

To try the **connect** flow instead (linking SSO to the existing local admin account): log in
locally first (the bootstrap admin credentials are logged on the backend's first-ever startup),
then visit Settings → Security → "Connect with SSO".

## Gotcha: Gradle daemon reuse

If you run the backend some other way than `task dev:oidc:backend` (e.g. hand-rolling the env
vars yourself), watch out: Gradle's daemon can get reused across invocations and silently keep
the *old* environment from whenever it was first started — the app will start fine but
`oidcEnabled` will read back `false` from `/api/auth/config` despite the vars being set in your
shell. Run `./gradlew --stop` first if that happens, or add `--no-daemon` (which is exactly why
the task does).

## Stopping it

```bash
task dev:oidc:down
```

## Regenerating secrets

Not needed — everything here is a fixed dev value. If you ever do want fresh ones:

```bash
# password hash for a new test user (paste into users_database.yml)
docker run --rm authelia/authelia:latest authelia crypto hash generate argon2 --password 'yourpassword' --no-confirm

# client secret hash (paste the digest into configuration.yml; use the plaintext as OIDC_CLIENT_SECRET)
docker run --rm authelia/authelia:latest authelia crypto hash generate pbkdf2 --password 'your-plaintext-secret' --no-confirm

# TLS cert for Authelia itself
openssl req -x509 -newkey rsa:2048 -keyout tls.key -out tls.crt -days 3650 -nodes \
  -subj "/CN=authelia-dev" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

# truststore for the backend to trust that cert
keytool -importcert -noprompt -trustcacerts -alias authelia-dev -file tls.crt \
  -keystore dev-truststore.p12 -storetype PKCS12 -storepass changeit
```
