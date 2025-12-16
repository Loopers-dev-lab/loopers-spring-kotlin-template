# Repository Guidelines

## Project Structure & Module Organization

This is a Gradle multi-module Kotlin/Spring Boot repository.

- `apps/`: runnable Spring Boot apps (e.g., `apps/commerce-api`, `apps/commerce-streamer`, `apps/pg-simulator`)
- `modules/`: reusable, domain-agnostic building blocks (`modules/jpa`, `modules/redis`, `modules/kafka`)
- `supports/`: cross-cutting add-ons (`supports/logging`, `supports/monitoring`, `supports/jackson`)
- Per module: `src/main/kotlin`, `src/main/resources`, tests in `src/test/kotlin`
- `docs/`: weekly design notes (`docs/week*`), `http/`: IntelliJ HTTP client requests, `docker/`: local infra/monitoring

## Architecture Overview (commerce-api)

- Layering: `com.loopers.domain` (business rules) → `com.loopers.application` (cross-domain `*Facade`) →
  `com.loopers.infrastructure` (adapters/repos) → `com.loopers.interfaces` (HTTP/events).
- Dependency rule: avoid horizontal dependencies between Services/Facades; keep cross-domain orchestration in Facades.
- Transactions/concurrency: transaction boundaries are typically at Facades; critical flows may use locking (e.g.,
  points/inventory).

## Build, Test, and Development Commands

Use the Gradle wrapper (`./gradlew`).

- `make init`: sets `core.hooksPath=.githooks` and enables pre-commit `ktlintCheck`
- `./gradlew build`: builds all modules
- `./gradlew test`: runs all tests (JUnit 5)
- `./gradlew :apps:commerce-api:bootRun`: run the main API app (module-specific tasks are preferred)
- `./gradlew :apps:commerce-streamer:bootRun` / `./gradlew :apps:pg-simulator:bootRun`: run other apps
- `./gradlew ktlintCheck` / `./gradlew ktlintFormat`: lint / auto-format Kotlin code
- `./gradlew jacocoTestReport`: generates JaCoCo XML coverage report
- `docker-compose -f ./docker/infra-compose.yml up`: starts local infra (DB/Redis/Kafka) for `local` profile
- `docker-compose -f ./docker/monitoring-compose.yml up`: starts monitoring (Grafana at `http://localhost:3000`)

## Coding Style & Naming Conventions

- Kotlin + Java toolchain 21; keep code idiomatic Kotlin (favor immutability, `data class` where appropriate).
- Formatting is enforced via `ktlint` and `.editorconfig` (line length 130; `*Test.kt` has no limit). Run
  `./gradlew ktlintFormat` before pushing.
- Naming: `UpperCamelCase` classes, `lowerCamelCase` functions/vars, `SCREAMING_SNAKE_CASE` constants, lowercase
  packages.
- `apps` may depend on `modules`/`supports`; avoid circular dependencies. In `commerce-api`, keep the `domain/` layer
  free of web/infra concerns; orchestrate cross-domain flows in `application/*Facade`.

## Testing Guidelines

- Frameworks: JUnit 5 + Spring Boot Test; mocks via SpringMockK/Mockito; E2E/integration coverage uses WireMock and
  Testcontainers (Docker required).
- Test runtime defaults (from Gradle): `spring.profiles.active=test`, timezone `Asia/Seoul`, `maxParallelForks=1` to
  reduce container/resource flakiness.
- Naming conventions: `*Test.kt`, `*IntegrationTest.kt`, `*E2ETest.kt` (see `apps/commerce-api/src/test/kotlin`).
- Run a single suite/class: `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.coupon.*Test"`.

## Commit & Pull Request Guidelines

- Commit messages follow a Conventional Commits-style pattern seen in history: `feat(scope): ...`,
  `refactor(scope): ...`, `docs: ...`, `test: ...`.
- PRs should follow `.github/pull_request_template.md`: include a short summary, review points, checklist items (
  tests/docs), and references/linked issues.

## Tool Usage

### Serena MCP

Prefer Serena for semantic code operations:

- Understanding codebase structure (`get_symbols_overview`)
- Finding symbols and their relationships (`find_symbol`, `find_referencing_symbols`)
- Navigating complex code paths

### Context7

Use for external library documentation lookup before implementing integrations.