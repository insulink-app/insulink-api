# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Spring Boot 4 backend (Java 25) serving the app's REST API. All endpoints live under the `/v1` context path.

## Commands

```bash
./gradlew build                                              # compile + test
./gradlew test                                               # all tests
./gradlew test --tests "de.insulink.api.iterator.AsyncIteratorTest"   # single test
./gradlew jacocoTestReport                                   # coverage report (XML goes to Codecov in CI)
./gradlew bootRun                                            # run locally (needs config.ini + Postgres)
./gradlew downloadGeoLite2Database                           # fetch geo/GeoLite2-City.mmdb (needs GEOLITE2_LICENSE_KEY)
docker compose up                                            # Postgres + app + nginx/certbot proxy
```

Runtime requires files that are **not** in the repo:
- `configurations/config.ini` — INI with sections like `[api]` (port, verification_key, authentication_key, refresh_key, allowed_origins) and DB connection settings. Loaded as a single `INIConfiguration` bean.
- `geo/GeoLite2-City.mmdb` — MaxMind GeoIP database for signup geolocation.

## Architecture

**Two application contexts.** `ApiApplication.main` first builds a plain `AnnotationConfigApplicationContext(ApiModule.class)` for infrastructure beans (`Log`, the `INIConfiguration` from `config.ini`, `HookRegistry`, `EventExecutor`), *then* starts the Spring web context via `SpringApplication(ApiApplication.class)`. The web server port comes from `ApiConfiguration`, injected as a default property before `run()`.

**Module pattern.** Each feature package has a `*Module` (`@Configuration`) that declares `@Bean`s, and a `*Configuration` POJO implementing the `Configuration` interface — its `load(INIConfiguration)` pulls typed values out of `config.ini`. Modules are wired together via `@Import` (e.g. `ApiModule` imports `api.ApiModule`).

**Custom event/hook system** (`event/`). `EventExecutor.execute(Event)` dispatches to hooks registered in `HookRegistry`; hooks are `Hook` implementations ordered by `HookPriority`. `ApplicationLaunchEvent` fires once the web context is up. This is separate from Spring's own events.

**Everything is async.** Repositories extend `DatabaseRepository<T, ID>` (a `@NoRepositoryBean` Spring Data interface) whose methods are `@Async` and return `CompletableFuture`. Controllers return `CompletableFuture<ApiResponse>` and chain with `thenCompose`/`thenApply`. `DatabaseRepository.generateAvailableId(...)` is the standard way to mint a collision-free UUID id. `AsyncIterator`/`AsyncListIterator` (in `iterator/`) coordinate batches of futures — these have the only unit tests.

**Persistence.** Postgres via Hibernate (`hbm2ddl.auto=update`, so the schema follows the entities). Entities live in feature packages and are picked up by `setPackagesToScan("de.insulink.api")`.

**Security / request flow.** Two `OncePerRequestFilter`s:
- `EquipmentFilter` — CORS headers; short-circuits `OPTIONS`. Allowed origins come from `config.ini`.
- `AppAuthenticationFilter` — validates the `Authorization: Bearer <JWT>` against `authenticationKey`. It only runs on endpoints whose handler method is annotated `@AppEndpoint`; `EndpointRepository` discovers these by reflecting over Spring's `RequestMappingHandlerMapping` at runtime.

Three JWT signing keys (`verificationKey`, `authenticationKey`, `refreshKey`) are `@Bean`s qualified by name in `api/ApiModule`, derived from `config.ini`. Authenticated app controllers extend `AppRestController`, which parses the JWT to recover the user id / session id.

**API conventions.** Inbound JSON is parsed with `ApiRequestBody.of(payload, response)` — use `getString` / `getBoolean` / `getSanitizedString` (the sanitized variant runs the OWASP HTML sanitizer; use it for any user-displayed free text). Responses use `ApiResponse.success(map)` / `ApiResponse.error(code)`, where `error` codes are app-specific integers (e.g. `1000`, `1001`); call `.future()` to wrap in a completed `CompletableFuture`.

**i18n.** `locale/*.json` (`de`, `en`) loaded via `Locales`; resolve strings through `Locale`/`LocaleString`/`Translation`.

## Testing an endpoint

Controller tests are MockMvc slices, one controller each. Annotate with
`@AppControllerTest(SomeController.class)` (`src/test/.../web/`): it boots that
controller alone, imports `TestAuthentication` for the `authenticationKey` /
`refreshKey` beans, and excludes both servlet filters (they run off `config.ini`
and the reflected endpoint list, and have their own tests). Repositories are
`@MockitoBean`.

- Build the `Authorization` header with `TestAuthentication.bearer(userId)` —
  the controller parses a real JWT, so a wrong or expired one behaves as it
  would in production (`bearerWithWrongSignature`, `expiredBearer` exist for
  that).
- Endpoints return `CompletableFuture`, which MockMvc leaves unfinished. Always
  go through `AsyncEndpoint.on(mockMvc).call(requestBuilder)`, never
  `mockMvc.perform` directly.
- Mockito stubs `DatabaseRepository`'s default methods too, so
  `generateAvailableId` hands back a `null` id unless it is stubbed. Stub it
  whenever the controller puts the generated id into the response.
- Pure logic (the sync reconcilers, `LogFormat`, the enums) gets a plain JUnit
  test with no context at all — prefer that whenever the class allows it.

## Code style (follow these — they override default habits)

- **Object-oriented.** Model behaviour as classes with state + instance methods.
- **Avoid static functions** — they work against OO. Prefer an instance method on
  the object that owns the data. Legitimate exceptions kept on purpose:
  `static final` constants (e.g. `Locale.LOCALE_PATH`), and **static factory
  readers** that construct the object they return — this codebase's house style is
  Lombok `@RequiredArgsConstructor(staticName = "create")` plus hand-written
  loaders like `Locale.createAndLoad(...)`, `ApiResponse.success()/error(...)`, and
  `ApiRequestBody.of(...)`. Use `new` only inside those factories.
- **No one-line `if`s.** Always use braces, even for a single statement.
- **No brace-block lambdas.** A lambda body stays a single expression — never a
  `{ … }` block, and especially never one with a `return` inside. When the body
  needs statements, extract it into a named private method and pass a
  method/expression reference instead (e.g. `.thenApply(items -> itemsResponse(...))`,
  not `.thenApply(items -> { … return …; })`). This keeps the async chains
  readable and the logic testable; it's how every existing controller is written.
- **No comments inside method bodies.** Keep methods short enough to read on their
  own; put the explanation in a Javadoc `/** … */` ABOVE the method (as the
  existing controllers and `AppRestController` already do).
- **All code comments in English.** Every comment and doc comment (`//`, `///`)
  is written in English — no German (or other languages). Only user-facing
  strings are localized (see below); the code itself, including its comments, is
  English. If you touch a file with a German comment, translate it while you're
  there.
- **Short methods and classes.** Split when they grow; one job each. Rule of
  thumb: **no Java file over ~150 lines**, methods ideally **5–10 lines**. When a
  flow needs more, decompose into a chain of small private overloads rather than
  one long method (see how `SignupController` splits `signupUser`/`completeSignup`).
  Treat these as hard smells, not hard limits.
- **Descriptive, unique names** for classes and methods — no generic or duplicated
  names. **No one-letter variable names** (loop counters included); the name says
  what it holds. Unnamed lambda/`catch` params written as `_` are the intentional
  exception (Java unnamed variables), not a short name.
- **Avoid boilerplate.** No scaffolding "for later", no repeated patterns a shared
  helper removes — extract the repetition. The async plumbing (`AsyncIterator`,
  `DatabaseRepository.generateAvailableId`, `ApiResponse`) exists so controllers
  don't re-implement it; reuse it.
- **2-space indentation** (see `.editorconfig`).
- **Lombok-first accessors.** `@Accessors(fluent = true)` so getters are `name()`
  not `getName()`; match this everywhere.
- **Localize everything.** No hard-coded user-facing strings — every displayed
  string goes through the locale layer: `LocaleString.of("key", args…)` resolved
  via `Locale.findText("key")`, backed by `src/main/resources/locale/*.json`.
- **JSON uses `snake_case` keys.** This holds for both the HTTP request/response
  bodies (`phone_number`, `public_key`, `verification_token`) and the locale
  files. Locale files are **nested objects** per section
  (`{"profile": {"glucose": {"target": …}}}`); `Locale.flatten` collapses them on
  load to dot-separated lookup keys (`profile.glucose.target`), so call sites stay
  `LocaleString.of("profile.glucose.target")`. A node that is BOTH a label and a
  prefix carries its own value under a `_` key (`profile.glucose._` = "Glucose"
  alongside `profile.glucose.target`).
- **Package by feature, NOT by layer.** Under `de.insulink.api` the top level is
  domain-based (`user/`, `statistic/`, `locale/`, `event/`, `hashing/`); a
  feature's state, entity, repository and logic live TOGETHER, and subfolders are
  sub-domains, never technical types (`user/session/`, `user/device/`,
  `statistic/installation/`, `statistic/opening/`). Cross-cutting infrastructure
  lives in `database/`, `event/`, `iterator/`, `log/`, `configuration/`. The one
  deliberate boundary layer is `api/` — the HTTP edge, organized by route group
  (`api/app/authentication/`, `api/security/`), not by type. Do NOT introduce
  generic `models/`, `services/`, `controllers/` folders elsewhere.
- **Document accumulated knowledge as individual markdown files under `docs/`** —
  one focused topic per file, rather than letting it pile up only in code.
