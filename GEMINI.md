# Loopers Template (Spring + Kotlin)

This is a multi-module Spring Boot project using Kotlin and Gradle, designed as a template for a commerce application.

## Project Overview

The project is structured into three main parts: `apps`, `modules`, and `supports`, each with a distinct responsibility.

- **`apps`**: Contains the main Spring Boot applications.
    - `commerce-api`: The main API for the commerce application.
    - `commerce-streamer`: A data streamer application, likely using Kafka.
    - `pg-simulator`: A payment gateway simulator.

- **`modules`**: Contains reusable configurations and components.
    - `jpa`: Provides JPA (Java Persistence API) configurations for database access.
    - `redis`: Provides Redis configurations, likely for caching.
    - `kafka`: Provides Kafka configurations for event-driven communication.

- **`supports`**: Contains add-on modules for cross-cutting concerns.
    - `jackson`: Provides custom configurations for JSON serialization.
    - `logging`: Provides centralized logging configurations.
    - `monitoring`: Provides monitoring capabilities, likely with Prometheus and Grafana.

### Layered Architecture (commerce-api)

The main application follows a strict layered architecture pattern:

```
com.loopers
├── domain/                    # Domain Layer
│   ├── user/
│   ├── product/
│   ├── order/
│   ├── point/
│   ├── like/
│   └── [Service classes]      # Single-domain business logic
├── application/               # Application Layer
│   └── [Facade classes]       # Cross-domain orchestration
├── infrastructure/            # Infrastructure Layer
│   └── [Repository implementations, JPA entities]
└── interfaces/                # Interface Layer
    └── api/                   # REST API controllers
```

## Building and Running

### Setup

To get started with the project, you need to set up the necessary tools and environment.

1. **Install `pre-commit` hooks**:
   The project uses `pre-commit` hooks to enforce code quality with `ktlint`. To install the hooks, run:
   ```shell
   make init
   ```

2. **Run required infrastructure**:
   The local environment relies on Docker to run necessary services like databases and message queues. You can start
   these services using the provided `docker-compose` file:
   ```shell
   docker-compose -f ./docker/infra-compose.yml up
   ```

3. **Run monitoring tools**:
   The project includes a monitoring setup with Prometheus and Grafana. To start the monitoring services, run:
   ```shell
   docker-compose -f ./docker/monitoring-compose.yml up
   ```
   You can then access the Grafana dashboard at `http://localhost:3000` with the username `admin` and password `admin`.

### Running the Applications

To run the applications, you can use the `./gradlew` script.

- **`commerce-api`**:
  ```shell
  ./gradlew :apps:commerce-api:bootRun
  ```

- **`commerce-streamer`**:
  ```shell
  ./gradlew :apps:commerce-streamer:bootRun
  ```

- **`pg-simulator`**:
  ```shell
  ./gradlew :apps:pg-simulator:bootRun
  ```

### Build and Test

```bash
./gradlew build                             # Build all modules
./gradlew test                              # Run all tests
./gradlew :apps:commerce-api:test          # Run tests for specific module

# Run specific test classes or packages
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.coupon.*Test"
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test --tests "com.loopers.domain.coupon.*Test"

# Test coverage report
./gradlew jacocoTestReport                 # Generate test coverage report
```

## Development Conventions

### Code Style

The project uses `ktlint` to enforce a consistent code style. The `pre-commit` hook ensures that all code is checked
before it is committed. You can also manually run the linter with:

```shell
./gradlew ktlintCheck                      # Check Kotlin code style
./gradlew ktlintFormat                     # Auto-format Kotlin code
```

### Modularity

The project is designed to be modular. When adding new features, consider whether they belong in an existing module or
if a new module should be created.

- `apps` modules can depend on `modules` and `supports`
- `modules` must be reusable and domain-agnostic
- No circular dependencies between modules

### Key Architectural Principles

**Service vs Facade Pattern:**

- **Service**: Handles single-domain business logic (e.g., `UserService`, `ProductService`, `OrderService`)
- **Facade**: Orchestrates cross-domain operations (e.g., `OrderFacade` coordinates order, product, and point domains)
- **Rule**: No horizontal dependencies between Services or Facades - only Facade can call multiple Services

**Concurrency Control:**

- Uses **pessimistic locking** for critical operations (point deductions, inventory management)
- Physical transactions guarantee cross-domain consistency
- Synchronous function calls between domains

**Transaction Management:**

- Physical database transactions ensure atomicity across domain operations
- Transaction boundaries are typically at the Facade layer
- Rollback scenarios properly handle multi-domain operations

## Domain-Specific Notes

### Current Domains

- **User**: User registration and profile management
- **Product**: Product catalog and inventory management
- **Order**: Order creation and management with inventory/point coordination
- **Point**: User point balance and transactions
- **Like**: Product likes/favorites functionality
- **Coupon**: Coupon issuance and usage (see `docs/week3/`)

For domain-specific implementation rules (locking strategies, caching, etc.), refer to spec documents in `docs/specs/`
or weekly design docs in `docs/`.

## Testing Strategy

### Test Types

- **Unit Tests**: Domain logic, value objects, business rules
- **Integration Tests**: Service layer with mocked repositories
- **E2E Tests**: Full API tests with test containers

### Test Configuration

- Tests run with `spring.profiles.active=test`
- Testcontainers used for PostgreSQL (requires Docker)
- Maximum 1 parallel fork for test execution to avoid resource conflicts
- JaCoCo for test coverage reporting
- Timezone set to `Asia/Seoul` for consistent date/time testing
- Test frameworks: JUnit 5, SpringMockK, Mockito-Kotlin, Instancio

## Important Files and Directories

- `settings.gradle.kts`: Module structure and plugin configuration
- `build.gradle.kts`: Root build configuration and shared dependencies
- `docs/specs/`: Feature requirements with corresponding design and implementation details
- `.githooks/pre-commit`: ktlint checks before commit
- `docker/infra-compose.yml`: Local infrastructure (PostgreSQL, Redis, Kafka)
- `docker/monitoring-compose.yml`: Monitoring stack (Prometheus, Grafana)

## Tool Usage

### Serena MCP

Prefer Serena for semantic code operations:

- Understanding codebase structure (`get_symbols_overview`)
- Finding symbols and their relationships (`find_symbol`, `find_referencing_symbols`)
- Navigating complex code paths

### Context7

Use for external library documentation lookup before implementing integrations.