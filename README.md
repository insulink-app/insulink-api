# Insulink API

The backend REST API powering **[Insulink](https://insulink.de)** — an open-source
glucose ecosystem that reads your CGM directly, so your data stays yours. This
repository is the server the phone apps and the [panel](https://panel.insulink.de)
talk to: accounts and sessions, the glucose/insulin/nutrition/sport history behind
the dashboards, and the plumbing to the forecast service. Spring Boot 4 on Java 25,
backed by Postgres. All endpoints live under the `/v1` path.

|      | Build Status                                                                                                                                                                                         | Coverage                                                                                                                                                |
|------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| main | [![Java CI with Gradle](https://github.com/insulink-app/insulink-api/actions/workflows/gradle.yml/badge.svg?branch=main)](https://github.com/insulink-app/insulink-api/actions/workflows/gradle.yml) | [![codecov](https://codecov.io/github/insulink-app/insulink-api/graph/badge.svg?token=Y2ZV4S3A9N)](https://codecov.io/github/insulink-app/insulink-api) |

> ⚠️ **Use at your own risk.** Insulink is an interoperability and research
> project, **not a medical device**. It is a companion for curious people, never
> a replacement for your approved devices or your care team, and must **never**
> be relied on for dosing or treatment decisions. This API stores personal health
> data — glucose readings, insulin doses, meals, heart rate and locations — so if
> you self-host it, you are the one responsible for that data. Not affiliated with
> or endorsed by Dexcom or Abbott.

## What it stores

Supported sensors are the **Dexcom G7** and the **Abbott FreeStyle Libre 3**
(`SensorType`); insulin is tracked as fast- and long-acting (`InsulinType`).
Around the accounts (users, sessions, devices, settings) the API keeps the
history the apps and the panel render:

| Area                                   | Holds                                                        |
|----------------------------------------|--------------------------------------------------------------|
| `glucose/`                             | readings in mg/dL, plus the forecast endpoint                |
| `insulin/`                             | basal and bolus entries                                      |
| `nutrition/`                           | meals, drinks and the product database                       |
| `sport/`                               | exercises, routines, workout sessions, cardio and GPS tracks |
| `health/`                              | daily aggregates, pulse samples, the live pulse cache        |
| `sensor/`, `pump/`                     | registered hardware and its state                            |
| `event/`                               | app-side events (e.g. `glucose_low`, `signal_loss`)          |
| `location/`                            | reported location entries                                    |
| `statistic/`, `analysis/`              | app installs and openings                                    |

Glucose forecasting is **not** done here: `PredictionClient` forwards the user id
and the freshest readings to the `predictor` sidecar (a Python service) and hands
back its reply.

## Build & run

```bash
# compile and run the test suite
./gradlew build

# run locally (needs configurations/config.ini and a Postgres instance)
./gradlew bootRun

# or bring up postgres + api + predictor + panel + nginx/certbot proxy together
docker compose up
```

You need JDK 25 (the Gradle wrapper handles Gradle itself). Two files are not in
the repo and must be provided before the app will start:

- `configurations/config.ini` — INI with an `[api]` section (port, the three JWT
  keys, allowed origins) and the Postgres connection settings.
- `geo/GeoLite2-City.mmdb` — MaxMind GeoIP database used for signup geolocation.
  Fetch it with `GEOLITE2_LICENSE_KEY=… ./gradlew downloadGeoLite2Database`.

## Architecture

A few things worth knowing before reading the code — `CLAUDE.md` has the full
tour:

- **Package by feature, not by layer.** A feature's entity, repository and logic
  live together (`glucose/`, `sport/`, `user/session/`). The one deliberate
  boundary is `web/` — the HTTP edge, organized by route group.
- **Everything is async.** Repositories extend `DatabaseRepository` and return
  `CompletableFuture`; controllers return `CompletableFuture<ApiResponse>` and
  chain with `thenCompose`/`thenApply`. `AsyncIterator`/`AsyncListIterator`
  (`iterator/`) run a batch of futures and settle once all of them have.
- **Two application contexts.** `ApiApplication.main` builds a plain context for
  infrastructure beans (config, logging, hooks) and *then* starts the Spring web
  context on the port from `config.ini`.
- **Custom event/hook system** (`event/`), separate from Spring's own events:
  `EventExecutor.execute(Event)` dispatches to `Hook`s ordered by `HookPriority`.
- **Auth.** `AppAuthenticationFilter` validates `Authorization: Bearer <JWT>`, but
  only on handlers annotated `@AppEndpoint`, which `EndpointRepository` discovers
  by reflection at startup. Authenticated controllers extend `AppRestController`.
- **Conventions.** Inbound JSON goes through `ApiRequestBody.of(...)`
  (`getSanitizedString` for anything user-displayed), replies through
  `ApiResponse.success(...)` / `.error(code)`. JSON keys are `snake_case`.
  User-facing strings are localized via `locale/*.json` — never hard-coded.

## Tests

```bash
./gradlew test                                                       # all tests
./gradlew test --tests "de.insulink.api.iterator.AsyncIteratorTest"  # a single test
./gradlew jacocoTestReport                                           # coverage → build/reports/jacoco
```

Two kinds of test, no database in either:

- **Plain JUnit** for the pieces every request routes through — the async
  batching in `iterator/`, the request/response edge (`ApiRequestBody`,
  `ApiResponse`), locale flattening, platform detection, and the sync
  reconcilers (`MeasurementSync`, `PulseSampleSync`).
- **Sliced MockMvc tests** for the endpoints. `@AppControllerTest(Xyz.class)`
  (in `src/test/.../web/`) boots one controller with a real signing key from
  `TestAuthentication` and no servlet filters; repositories come in as
  `@MockitoBean`. Endpoints return `CompletableFuture`, so requests go through
  `AsyncEndpoint.on(mockMvc).call(...)`, which does the second dispatch. The two
  filters have their own tests, since their status codes (`417` refresh, `403`
  clear session) are contract.

Coverage is reported to [Codecov](https://codecov.io/github/insulink-app/insulink-api)
from the JaCoCo XML on every CI run; `codecov.yml` asks for no drop on a pull
request rather than an absolute number.

## Contributions

Contributions are welcome. For larger changes please open an issue first so we
can discuss the approach (see `CONTRIBUTING.md`). Continuous integration runs
`gradle build` — compile plus the full test suite — on every push and pull
request to `main`, so please keep it green. Test reports are kept as a run
artifact, so a red build can be read after the fact.

## License

Licensed under **AGPL-3.0** (see `LICENSE.md`).
