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
| Spring Batch Step pipeline verification              | Integration | `*StepIntegrationTest.kt` |
| Spring Batch Job with branching logic                | Integration | `*JobIntegrationTest.kt` |

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
                            └── No → Is it Spring Batch component?
                                     ├── Yes → See Batch Decision Flow below
                                     └── No → Integration Test
```

### Batch Decision Flow

```
Spring Batch testing?
├── Business logic in Processor?
│   └── Move to Domain Service → Unit Test the service, not Processor
├── Step pipeline verification?
│   └── Step Integration Test (launchStep) - PRIMARY PATTERN
└── Job with conditional flow (Decider, on("FAILED"))?
    └── Job Integration Test (launchJob)
```

## When in Doubt

1. **Start with Unit Test** - If you can test it without Spring context, do so
2. **Elevate only when necessary** - Move to Integration only when real DB is required
3. **E2E is for contracts** - If you're testing business logic in E2E, you're doing it wrong
4. **Concurrency is special** - Always separate into dedicated test file
5. **Batch logic belongs in Domain** - Don't test Processor; move logic to Domain Service
6. **Step Integration is primary** - Verify pipeline wiring; Job tests only for complex branching
