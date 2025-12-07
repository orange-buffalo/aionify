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
- **Build Tools**: Gradle for backend, **Bun for frontend** (never commit `package-lock.json`)
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

**After every task implementation, the E2E tests must pass:**

The E2E tests validate the application running from the Docker image. To run them locally:

```bash
# First, build the Docker image
./gradlew build \
  -Dquarkus.package.jar.enabled=false -Dquarkus.native.enabled=true \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.tag=local-test \
  --build-cache --console=plain

# Install Playwright browsers if not already installed
./gradlew installPlaywrightBrowsers

# Run E2E tests
./gradlew e2eTest -Daionify.docker.image=ghcr.io/orange-buffalo/aionify:local-test
```

The E2E tests ensure that:
- The Docker image builds correctly
- The application starts successfully in a production-like environment
- Critical user flows (like login) work end-to-end

### Running Specific Tests

To run a specific test class:
```bash
./gradlew test --tests "io.orangebuffalo.aionify.FrontendPlaywrightTest"
```

## Project Structure

- `src/main/kotlin/io/orangebuffalo/aionify/` - Main Kotlin application code
  - `config/` - Configuration classes (e.g., JDBI setup)
  - `domain/` - Domain models and repositories
- `src/test/kotlin/io/orangebuffalo/aionify/` - Unit tests (Quarkus test mode)
- `src/e2eTest/kotlin/io/orangebuffalo/aionify/` - E2E tests (Docker image tests)
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

### Unit Tests (src/test)

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

### E2E Tests (src/e2eTest)

E2E tests validate the application running in Docker. These tests:
- Use Testcontainers to start the application with Docker Compose
- Test the production Docker image (native binary)
- Validate critical user flows in a production-like environment
- Are located in `src/e2eTest/kotlin`

E2E tests run automatically in CI after the Docker image is built and pushed.

## Database

- Use Flyway for database migrations (place in `src/main/resources/db/migration/`)
- Use JDBI for database access with Kotlin extensions
- PostgreSQL is the target database
