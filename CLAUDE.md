# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Loopers Spring Kotlin Template - A multi-module monolithic e-commerce application built with Kotlin and Spring Boot.
This project implements an e-commerce system with products, orders, points, likes, and coupons.

## Essential Commands

### Initial Setup

```bash
make init                                    # Setup git hooks for pre-commit ktlint checks
```

### Infrastructure

```bash
# Start local infrastructure (PostgreSQL, Redis, Kafka)
docker-compose -f ./docker/infra-compose.yml up

# Start monitoring stack (Prometheus + Grafana at http://localhost:3000)
docker-compose -f ./docker/monitoring-compose.yml up
```

### Build and Test

```bash
./gradlew build                             # Build all modules
./gradlew test                              # Run all tests
./gradlew :apps:commerce-api:test          # Run tests for specific module
./gradlew :apps:commerce-api:bootRun       # Run the commerce-api application
./gradlew :apps:commerce-streamer:bootRun  # Run the Kafka streaming application

# Run specific test classes or packages
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.coupon.*Test"
./gradlew :apps:commerce-api:cleanTest :apps:commerce-api:test --tests "com.loopers.domain.coupon.*Test"

# Test coverage report
./gradlew jacocoTestReport                 # Generate test coverage report
```

### Code Quality

```bash
./gradlew ktlintCheck                      # Check Kotlin code style
./gradlew ktlintFormat                     # Auto-format Kotlin code
```

## Architecture Overview

### Multi-Module Structure

The project follows a strict module hierarchy with clear boundaries:

```
Root
├── apps/           # Executable Spring Boot applications
│   ├── commerce-api      # Main REST API application (HTTP endpoints)
│   └── commerce-streamer # Kafka streaming application (event processing)
├── modules/        # Reusable, domain-agnostic configurations
│   ├── jpa         # JPA configuration and utilities
│   ├── redis       # Redis configuration and cache templates
│   └── kafka       # Kafka configuration
└── supports/       # Add-on modules for cross-cutting concerns
    ├── jackson     # JSON serialization config
    ├── logging     # Logging configuration
    └── monitoring  # Metrics and monitoring (Prometheus/Grafana)
```

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

## Development Guidelines

### Module Dependencies

- `apps` modules can depend on `modules` and `supports`
- `modules` must be reusable and domain-agnostic
- No circular dependencies between modules

### Code Style

- Kotlin with Java 21 toolchain
- ktlint enforced via pre-commit hooks
- All code must pass ktlint checks before commit

### Documentation

- Mixed Korean/English documentation
- Design documents in `docs/` directory organized by week
- API documentation should follow REST conventions

## Important Files and Directories

- `settings.gradle.kts`: Module structure and plugin configuration
- `build.gradle.kts`: Root build configuration and shared dependencies
- `docs/specs/`: Feature requirements with corresponding design and implementation details
- `.githooks/pre-commit`: ktlint checks before commit
- `docker/infra-compose.yml`: Local infrastructure (PostgreSQL, Redis, Kafka)
- `docker/monitoring-compose.yml`: Monitoring stack (Prometheus, Grafana)

## Tool Usage

- **Serena**: Use for file navigation, symbol search, and code modifications
- **Context7**: Use for library documentation lookup when implementing with external libraries