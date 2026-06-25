# Insulink API

The backend REST API powering the **Insulink** Dexcom G7 glucose app. It handles user accounts, sessions and devices, app statistics and localization. Built with Spring Boot 4 on Java 25, backed by Postgres. All endpoints live under the `/v1` path.

|      | Build Status                                                                                                                                                                            |
|------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| main | [![Java CI with Gradle](https://github.com/insulink-app/insulink-api/actions/workflows/gradle.yml/badge.svg?branch=main)](https://github.com/insulink-app/insulink-api/actions/workflows/gradle.yml) |

> ⚠️ **Use at your own risk.** Insulink is an interoperability and research
> project, **not a medical device**. This API stores no sensor readings and must
> **never** be relied on for dosing or treatment decisions. Not affiliated with
> or endorsed by Dexcom.

## Build & run

```bash
# compile and run the test suite
./gradlew build

# run locally (needs configurations/config.ini and a Postgres instance)
./gradlew bootRun

# or bring up Postgres + app + nginx/certbot proxy together
docker compose up
```

You need JDK 25 (the Gradle wrapper handles Gradle itself). Two files are not in
the repo and must be provided before the app will start:

- `configurations/config.ini` — INI with an `[api]` section (port, the three JWT
  keys, allowed origins) and the Postgres connection settings.
- `geo/GeoLite2-City.mmdb` — MaxMind GeoIP database used for signup geolocation.
  Fetch it with `GEOLITE2_LICENSE_KEY=… ./gradlew downloadGeoLite2Database`.

## Tests

```bash
./gradlew test                                                       # all tests
./gradlew test --tests "de.insulink.api.iterator.AsyncIteratorTest"  # a single test
```

## Contributions

Contributions are welcome. For larger changes please open an issue first so we
can discuss the approach (see `CONTRIBUTING.md`). Continuous integration runs
`gradle build` — compile plus the full test suite — on every push and pull
request to `main`, so please keep it green.

## License

Licensed under **AGPL-3.0** (see `LICENSE.md`).
