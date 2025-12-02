# GitHub Copilot Instructions

This document provides instructions for GitHub Copilot when working on this repository.

**Important**: Keep this document up to date after every change that affects it. Extend it with necessary information when new approaches are introduced, new code infrastructure is added, or existing patterns change.

## Documentation

Project documentation is organized as follows:
- `README.md` - Brief project overview and links to detailed docs
- `docs/deployment.md` - Production deployment guide (configuration, Docker, admin setup)
- `docs/development.md` - Local development setup guide
- `.github/copilot-instructions.md` - This file, containing coding guidelines and conventions

**When making changes that affect deployment or development workflows, update the corresponding documentation files.**

## Tech Stack Overview

- **Backend**: Quarkus framework with Kotlin
- **Frontend**: React with TypeScript, shadcn-ui components, and Tailwind CSS v4
- **Build Tools**: Gradle for backend, Bun for frontend
- **Database**: PostgreSQL with Flyway migrations and JDBI for data access
- **Testing**: JUnit 5 with Playwright for E2E tests

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
./gradlew test --tests "io.orangebuffalo.aionify.FrontendPlaywrightTest"
```

## Project Structure

- `src/main/kotlin/io/orangebuffalo/aionify/` - Main Kotlin application code
  - `config/` - Configuration classes (e.g., JDBI setup)
  - `domain/` - Domain models and repositories
- `src/test/kotlin/io/orangebuffalo/aionify/` - Test code
- `frontend/` - React frontend application
  - `src/components/ui/` - shadcn-ui components
  - `src/lib/` - Utility functions
- `docs/` - Project documentation
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

We focus on UI-level testing using Playwright. The REST API is considered an internal implementation detail.

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
