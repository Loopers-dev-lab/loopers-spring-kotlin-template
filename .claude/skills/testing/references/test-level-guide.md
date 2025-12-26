# Test Level Guide

This document defines test level classification criteria and decision flow for determining which level each test case belongs to.

## Test Level Classification

| What to Test?                                        | Level       | File Naming           |
|------------------------------------------------------|-------------|-----------------------|
| Domain logic without external dependencies           | Unit        | `*Test.kt`            |
| Orchestration with DB/external dependencies          | Integration | `*IntegrationTest.kt` |
| Message Queue Consumer processing                    | Integration | `*IntegrationTest.kt` |
| Concurrency control (locks, duplicate prevention)    | Concurrency | `*ConcurrencyTest.kt` |
| External API clients, complex DB queries, resilience | Adapter     | `*AdapterTest.kt`     |
| Full API request/response                            | E2E         | `*ApiE2ETest.kt`      |

## Decision Flow

```
Does this test require external dependencies?
├── No → Unit Test
└── Yes → Does it test concurrency/locking?
          ├── Yes → Concurrency Test
          └── No → Does it test external API client or resilience?
                   ├── Yes → Adapter Test
                   └── No → Is it testing full HTTP request/response?
                            ├── Yes → E2E Test
                            └── No → Integration Test
```

## When in Doubt

1. **Start with Unit Test** - If you can test it without Spring context, do so
2. **Elevate only when necessary** - Move to Integration only when real DB is required
3. **E2E is for contracts** - If you're testing business logic in E2E, you're doing it wrong
4. **Concurrency is special** - Always separate into dedicated test file
