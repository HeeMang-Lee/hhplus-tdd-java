# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a TDD (Test-Driven Development) training project for implementing a point management system. The project is part of the "항해플러스" (Hanghae Plus) curriculum, focusing on practicing TDD methodology with Java and Spring Boot.

**Core Features to Implement:**
- User point inquiry (조회)
- Point charging (충전)
- Point usage (사용)
- Point transaction history (내역 조회)

**Key Development Requirements:**
- Follow Red-Green-Refactor cycle
- Write unit tests for all features
- Implement concurrency control for point operations
- Document concurrency control approach in README.md

## Build & Development Commands

**Build:**
```bash
./gradlew build
```

**Run Tests:**
```bash
./gradlew test
```

**Run Single Test Class:**
```bash
./gradlew test --tests ClassName
```

**Run Single Test Method:**
```bash
./gradlew test --tests ClassName.methodName
```

**Run Application:**
```bash
./gradlew bootRun
```

**Test Coverage (JaCoCo):**
```bash
./gradlew test jacocoTestReport
# Report location: build/reports/jacoco/test/html/index.html
```

**Clean Build:**
```bash
./gradlew clean build
```

## Architecture & Code Structure

### Package Organization
- `io.hhplus.tdd.point` - Domain layer for point management
  - `PointController` - REST endpoints for point operations
  - `UserPoint` - Point balance record (immutable)
  - `PointHistory` - Transaction history record (immutable)
  - `TransactionType` - Enum for CHARGE/USE operations

- `io.hhplus.tdd.database` - Data access layer (in-memory tables)
  - `UserPointTable` - User point storage with built-in latency simulation
  - `PointHistoryTable` - Transaction history storage with built-in latency simulation

- `io.hhplus.tdd` - Application root
  - `TddApplication` - Spring Boot main class
  - `ApiControllerAdvice` - Global exception handler
  - `ErrorResponse` - Standard error response format

### Critical Constraints

**DO NOT MODIFY Table Classes:**
The `UserPointTable` and `PointHistoryTable` classes in the `database` package must NOT be modified. These classes:
- Simulate database latency (200-300ms random delay per operation)
- Use in-memory HashMap/List for storage
- Provide public APIs that must be used as-is for data access
- Are designed to expose concurrency issues during testing

**Table APIs Available:**
- `UserPointTable.selectById(Long id)` - Get user point (200ms latency)
- `UserPointTable.insertOrUpdate(long id, long amount)` - Save point (300ms latency)
- `PointHistoryTable.insert(long userId, long amount, TransactionType type, long updateMillis)` - Add history (300ms latency)
- `PointHistoryTable.selectAllByUserId(long userId)` - Get user's history

### Concurrency Considerations

The built-in latency in Table classes makes race conditions highly likely. When implementing point charge/use operations:
- Multiple concurrent requests to the same user will cause data inconsistency
- Implement proper concurrency control (e.g., synchronized blocks, locks, or atomic operations)
- Test with concurrent scenarios to verify thread safety
- Document your concurrency control approach in README.md

## Testing Strategy

This project emphasizes TDD:
1. Write failing test first (Red)
2. Write minimum code to pass (Green)
3. Refactor while keeping tests green (Refactor)

**Test Categories:**
- Unit tests - Test individual components in isolation
- Integration tests - Test API endpoints with Spring context
- Concurrency tests - Verify thread-safety under concurrent load

**Spring Boot Test Support:**
- JUnit 5 (Jupiter) is configured
- Spring Boot Test starter available for integration tests
- Test failures don't stop the build (`ignoreFailures = true`)

## Technology Stack

- Java 17
- Spring Boot 3.2.0
- Spring Cloud 2023.0.0
- Gradle (Kotlin DSL)
- Lombok for boilerplate reduction
- JaCoCo for test coverage
- JUnit 5 for testing

## Pull Request Guidelines

When creating PRs, use the template format:
- Title: `[STEP0X] 이희망`
- Include checklist completion status
- Link relevant commits
- Add review points/questions
- Include brief retrospective (3 lines max)

## Notes for Development

- The project uses Java records for immutable data (`UserPoint`, `PointHistory`, `ErrorResponse`)
- Global exception handling is configured in `ApiControllerAdvice`
- The controller currently has stub implementations (returns empty/zero values)
- Follow TDD principles: implement features iteratively with tests driving design
