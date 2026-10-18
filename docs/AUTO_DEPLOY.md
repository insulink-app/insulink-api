# Automatic deployment

A merge to `main` reaches the server by itself, within about five minutes:

1. CI (`.github/workflows/gradle.yml`, job `publish`) builds the jar and pushes
   `ghcr.io/insulink-app/insulink-api:latest` (plus a `sha-<commit>` tag). The
   panel and predictor repos do the same for `insulink-panel` and
   `insulink-predictor`.
2. On the server a systemd timer (`deploy/insulink-update.timer`) runs every
   five minutes: `git pull --ff-only` of this repo (so compose and proxy changes
   arrive too), `docker compose pull api panel predictor proxy`,
   `docker compose up -d`, then `nginx -s reload` in the proxy. Compose only
   restarts a container whose image or configuration changed.

## Why pull and not push

The repos are public. Pulling means GitHub holds no SSH key and no URL into the
server; a compromised workflow can at worst publish a bad image, not log in.
The publish job runs on `push` only, so a fork's pull request never reaches the
registry. No self-hosted runner: on a public repo a stranger's pull request
would run on it.

## The image holds no secrets

Anyone can pull the image, so `configurations/` (JWT keys, database
credentials) and `geo/` (GeoLite, whose license forbids redistribution) are not
in it. Compose mounts both read-only from the server's checkout; `.dockerignore`
keeps them out of the build context as well. The Postgres password comes from
`.env` (`POSTGRES_PASSWORD`), not from the public compose file. Use URL-safe
characters only: it is also spliced into the predictor's `DATABASE_URL`.

Postgres, api and predictor bind to `127.0.0.1`. Docker's port rules bypass
`ufw`, so a bare `"5432:5432"` publishes the database to the internet; only the
nginx proxy listens publicly.

## Server setup (once)

1. The GHCR packages must be **public** (GitHub → package → settings → change
   visibility), or the server needs `docker login ghcr.io`.
2. `.env` next to `docker-compose.yml` holds `POSTGRES_PASSWORD` and
   `CLAUDE_CODE_OAUTH_TOKEN`. On an existing volume Postgres keeps the password
   it was created with: change it with
   `docker compose exec postgres psql -U postgres -c "ALTER USER postgres PASSWORD '<new>'"`,
   then put the same value into `.env` and the database section of `config.ini`.
3. Adjust `WorkingDirectory` in `deploy/insulink-update.service` to the
   checkout, then:
   ```
   sudo cp deploy/insulink-update.* /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now insulink-update.timer
   ```
4. Check a run: `journalctl -u insulink-update.service -n 50`.

The checkout must stay clean: a local edit to a tracked file makes
`git pull --ff-only` fail, and the unit stops before touching any container.
## The proxy and Postgres

The proxy is the stock `nginx:alpine` with `proxy/default.conf` from this repo
mounted. A changed config file does not recreate the container, so the timer
reloads nginx on every run; that also picks up certificates certbot renewed.
`nginx -s reload` checks the new config first and keeps serving the old one if
it is broken, so a bad merge never takes the sites down (the unit run shows as
failed in the journal).

Postgres is deliberately not in the pull list: `postgres:latest` can jump a
major version, and a data directory from one major version does not start
under the next. Upgrade it by hand (dump, new volume, restore).
