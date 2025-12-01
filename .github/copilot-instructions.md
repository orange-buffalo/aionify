# GitHub Copilot Instructions

This document provides instructions for GitHub Copilot when working on this repository.

## Tech Stack Overview

- **Backend**: Quarkus framework with Kotlin
- **Frontend**: React with TypeScript, shadcn-ui components, and Tailwind CSS v4
- **Build Tools**: Gradle for backend, Bun for frontend
- **Database**: PostgreSQL with Flyway migrations and JDBI for data access
- **Testing**: JUnit 5 with REST Assured for API tests, Playwright for E2E tests

## Development Workflow

### Before Pushing Changes

**Always run the build locally before pushing changes:**

```bash
./gradlew build --build-cache --console=plain
```

This ensures:
- Code compiles correctly
- All tests pass
- No regressions are introduced

### Running Specific Tests

To run a specific test class:
```bash
./gradlew test --tests "org.aionify.FrontendPlaywrightTest"
```

### Playwright Browser Setup

Playwright tests require browser binaries. Install them with:
```bash
./gradlew installPlaywrightBrowsers
```

## Project Structure

- `src/main/kotlin/org/aionify/` - Main Kotlin application code
  - `config/` - Configuration classes (e.g., JDBI setup)
  - `domain/` - Domain models and repositories
- `src/test/kotlin/org/aionify/` - Test code
- `frontend/` - React frontend application
  - `src/components/ui/` - shadcn-ui components
  - `src/lib/` - Utility functions
- `.github/workflows/` - CI/CD workflows
- `.github/actions/` - Custom GitHub Actions

## Coding Conventions

### Kotlin/Backend

- Use data classes for domain models
- Use `@ApplicationScoped` for CDI beans
- Use JDBI with Kotlin extensions for database access
- Follow Kotlin naming conventions (camelCase for functions/properties)

### TypeScript/Frontend

- Use TypeScript with strict mode
- Use shadcn-ui components from `@/components/ui/`
- Use path aliases: `@/components`, `@/lib`, `@/hooks`
- Add `data-testid` attributes to elements that need E2E testing
- Use Tailwind CSS v4 for styling

## Testing Guidelines

### REST API Tests

Use REST Assured with Kotlin extensions:
```kotlin
@QuarkusTest
class MyResourceTest {
    @Test
    fun `test endpoint`() {
        given()
            .`when`().get("/api/endpoint")
            .then()
            .statusCode(200)
    }
}
```

### Playwright Tests

Playwright tests should extend `PlaywrightTestBase` which provides:
- Automatic browser lifecycle management
- Trace recording for debugging
- Clean test structure without boilerplate

Example:
```kotlin
@QuarkusTest
class MyPlaywrightTest : PlaywrightTestBase() {
    @TestHTTPResource("/")
    lateinit var url: URL

    @Test
    fun `my test`() {
        page.navigate(url.toString())
        // assertions...
    }
}
```

Traces are saved to `build/playwright-traces/` and uploaded as CI artifacts.

## Database

- Use Flyway for database migrations (place in `src/main/resources/db/migration/`)
- Use JDBI for database access with Kotlin extensions
- PostgreSQL is the target database
