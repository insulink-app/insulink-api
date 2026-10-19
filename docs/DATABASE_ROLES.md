# Database roles

Out of the box the api and the predictor both connect as the superuser
`postgres`. A bug or a breach in either then owns the whole cluster. Two roles
of their own limit that:

| Role | Used by | Rights |
|---|---|---|
| `insulink_api` | api (`config.ini` `[database] username`) | owns the tables; Hibernate's `hbm2ddl=update` needs that to add columns |
| `insulink_predictor` | predictor (`.env` `PREDICTOR_DB_*`) | `SELECT` only; it never writes to the database, its models go to disk |

`docker-compose.yml` keeps working without them: the predictor falls back to
`postgres` until `PREDICTOR_DB_PASSWORD` is set, and the api uses whatever
`config.ini` names. So the switch is done by hand, in this order.

## 1. Create the roles

Use URL-safe passwords (the predictor's goes into a connection URL).

```
docker compose exec -T postgres psql -U postgres -d insulink <<'SQL'
CREATE ROLE insulink_api LOGIN PASSWORD '<api password>';
CREATE ROLE insulink_predictor LOGIN PASSWORD '<predictor password>';

ALTER SCHEMA public OWNER TO insulink_api;
DO $$
DECLARE item record;
BEGIN
  FOR item IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
    EXECUTE format('ALTER TABLE public.%I OWNER TO insulink_api', item.tablename);
  END LOOP;
  FOR item IN SELECT sequencename FROM pg_sequences WHERE schemaname = 'public' LOOP
    EXECUTE format('ALTER SEQUENCE public.%I OWNER TO insulink_api', item.sequencename);
  END LOOP;
END $$;

GRANT CONNECT ON DATABASE insulink TO insulink_predictor;
GRANT USAGE ON SCHEMA public TO insulink_predictor;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO insulink_predictor;
ALTER DEFAULT PRIVILEGES FOR ROLE insulink_api IN SCHEMA public
  GRANT SELECT ON TABLES TO insulink_predictor;
SQL
```

The default privileges make every table the api creates later readable for
the predictor as well.

## 2. Point the services at them

- `api/configurations/config.ini`, section `[database]`: `username =
  insulink_api`, `password = <api password>`.
- `.env`: `PREDICTOR_DB_USER=insulink_predictor` and
  `PREDICTOR_DB_PASSWORD=<predictor password>`.
- `docker compose up -d` (recreates the predictor), then
  `docker compose restart api`.

## 3. Check

- `docker compose logs api` shows no permission errors after a minute.
- `curl -s localhost:8000/` answers, and a forecast in the app still loads.
- The host's own training (`uv run gf ...` against `127.0.0.1:5432`) can keep
  using `postgres`, or the predictor role, since it only reads.

## Predictor admin token

`POST /admin/retrain` on the predictor is switched off unless
`PREDICTOR_ADMIN_TOKEN` is set in `.env`. With it set, send it as
`Authorization: Bearer <token>`:

```
curl -X POST -H "Authorization: Bearer $PREDICTOR_ADMIN_TOKEN" localhost:8000/admin/retrain
```
