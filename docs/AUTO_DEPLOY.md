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
in it. Compose mounts both read-only from `api/` on the server; `.dockerignore`
keeps them out of the build context as well. The Postgres password comes from
`.env` (`POSTGRES_PASSWORD`), not from the public compose file. Use URL-safe
characters only: it is also spliced into the predictor's `DATABASE_URL`.

The containers sit on two networks: `data` (Postgres, api, predictor) and
`edge` (proxy, panel, api), so the public-facing proxy and panel cannot reach
the database or the predictor at all.

Postgres, api and predictor bind to `127.0.0.1`. Docker's port rules bypass
`ufw`, so a bare `"5432:5432"` publishes the database to the internet; only the
nginx proxy listens publicly.

## The folder on the server

The server holds no source code, only what the containers mount. It is a
sparse clone of this repo with exactly three tracked paths checked out, plus
the server-only state that `.gitignore` keeps out of git:

```
/home/insulink/deploy/
  docker-compose.yml           tracked
  proxy/default.conf           tracked
  deploy/insulink-update.*     tracked
  .env                         POSTGRES_PASSWORD, CLAUDE_CODE_OAUTH_TOKEN,
                               optional PREDICTOR_DB_USER/PREDICTOR_DB_PASSWORD
                               and PREDICTOR_ADMIN_TOKEN (docs/DATABASE_ROLES.md)
  api/configurations/          config.ini
  api/geo/                     GeoLite2-City.mmdb
  predictor/artifacts/         the trained per-user models
  certbot/                     www/ and conf/ (the certificates)
```

Compose pins `name: insulink`, so the Postgres volume stays `insulink_pgdata`
whatever the folder is called. For local development the same layout applies:
`docker compose` mounts `./api/configurations`, while `./gradlew bootRun`
still reads `./configurations`.

## Server setup (once)

1. The GHCR packages (`insulink-api`, `insulink-panel`, `insulink-predictor`)
   must be **public** (GitHub, package settings, change visibility), or the
   server needs `docker login ghcr.io`.
2. Back up the database first:
   `docker exec insulink-postgres pg_dumpall -U postgres > ~/insulink-backup.sql`.
   Check `docker volume ls` shows `insulink_pgdata`. A folder named
   `insulink` (or the home of user `insulink`) gave exactly that name; with
   another prefix, change the `name:` pin in this repo first, or Postgres
   starts on an empty volume.
3. Move the old hand-built folder into the layout above. `OLD` is wherever
   `docker-compose.yml`, `build`, `Dockerfile`, `panel` and `predictor` lie
   today:
   ```
   OLD=~/insulink
   cd "$OLD" && docker compose down
   mkdir -p ~/deploy/api ~/deploy/predictor
   mv configurations geo ~/deploy/api/
   mv predictor/artifacts ~/deploy/predictor/
   mv certbot .env ~/deploy/
   rm -rf build Dockerfile panel predictor proxy docker-compose.yml
   cd ~/deploy
   git init -b main
   git remote add origin https://github.com/insulink-app/insulink-api.git
   git sparse-checkout set --no-cone /docker-compose.yml /proxy/ /deploy/
   git pull origin main
   git branch -u origin/main
   ```
   The `rm` names each old item on purpose, so it is safe even when `OLD` is
   the home folder itself. Everything it deletes is in the images now.
4. Add `POSTGRES_PASSWORD=<new>` to `.env` (next to `CLAUDE_CODE_OAUTH_TOKEN`);
   compose refuses to start without it. On an existing volume Postgres keeps
   the password it was created with, so set the new one inside:
   `docker compose up -d postgres`, then
   `docker compose exec postgres psql -U postgres -c "ALTER USER postgres PASSWORD '<new>'"`,
   and put the same value into the database section of
   `api/configurations/config.ini`.
5. `docker compose pull && docker compose up -d`, then install the timer:
   ```
   sudo cp deploy/insulink-update.* /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now insulink-update.timer
   ```
   The unit runs as user `insulink` in `/home/insulink/deploy` (git refuses
   to pull as root in a folder another user owns); that user must be in the
   `docker` group. Edit the copy in `/etc/systemd/system/` for another
   user or folder.
6. Check a run: `journalctl -u insulink-update.service -n 50`.

Leave the tracked files alone on the server: a local edit makes
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
