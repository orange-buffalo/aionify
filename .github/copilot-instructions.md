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

- **Backend**: Micronaut framework with Kotlin
- **Frontend**: React with TypeScript, shadcn-ui components, and Tailwind CSS v4
- **Build Tools**: Gradle for backend, **Bun for frontend** (never commit `package-lock.json`)
- **Database**: PostgreSQL with Flyway migrations and Micronaut Data JDBC for data access
- **Testing**: JUnit 5 with Playwright for E2E tests and Testcontainers for database integration

## Development Workflow

### Code Formatting

**CRITICAL: Always format code before committing:**

```bash
./gradlew format
```

This runs:
- `ktlintFormat` for Kotlin code
- `prettierFormat` for frontend code (TypeScript, React, etc.)

The `check` task automatically verifies formatting and will fail if code is not properly formatted.

### Before Pushing Changes

**CRITICAL: This is an unbreakable rule - Always run tests locally before pushing changes:**

```bash
./gradlew build --console=plain
```

This ensures:
- Code compiles correctly
- All tests pass
- Code is properly formatted
- No regressions are introduced

**You MUST verify that all tests pass locally before marking any task as complete or ready for review. Never hide failing test results. If tests fail locally, you must fix them before proceeding.**

**After every task implementation, the E2E tests must pass:**

The E2E tests validate the application running from the Docker image. To run them locally:

```bash
# First, build the Docker image
./gradlew nativeCompile
docker build -f src/main/docker/Dockerfile.native \
  -t ghcr.io/orange-buffalo/aionify:local-test .

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
- `src/test/kotlin/io/orangebuffalo/aionify/` - Unit tests (Micronaut test mode)
- `src/e2eTest/kotlin/io/orangebuffalo/aionify/` - E2E tests (Docker image tests)
- `frontend/` - React frontend application
  - `src/components/ui/` - shadcn-ui components
  - `src/lib/` - Utility functions
- `docs/` - Project documentation
- `.github/workflows/` - CI/CD workflows
- `.github/actions/` - Custom GitHub Actions

## Coding Conventions

### Kotlin/Backend

- Use data classes for domain models annotated with `@MappedEntity` for Micronaut Data
- Use `@Singleton` for dependency injection (Micronaut CDI)
- Use Micronaut Data JDBC with repository interfaces (`@JdbcRepository`)
- Follow Kotlin naming conventions (camelCase for functions/properties)
- All DTOs used in REST endpoints must have `@Introspected` annotation for serialization/validation
- **CRITICAL: All error responses MUST include an `errorCode` field** for frontend internationalization (see UserResource.kt and UserAdminResource.kt for examples)

### Public API (`/api/**`)

- All public API endpoints must be in the `io.orangebuffalo.aionify.api` package
- All endpoints must use OpenAPI annotations (`@Operation`, `@ApiResponse`, `@SecurityRequirement`, etc.)
- **CRITICAL: When adding or modifying public API endpoints, always update OpenAPI schema annotations**
  - Use `@Operation` to describe the endpoint
  - Use `@ApiResponse` for all possible response codes (200, 401, 429, etc.)
  - Use `@SecurityRequirement(name = "BearerAuth")` to indicate authentication is required
  - Use `@Tag` to group related endpoints
  - Use `@Schema` on DTOs to describe fields
- Public API uses Bearer token authentication (UserApiAccessToken)
- Rate limiting is automatically applied (10 failed attempts = 10 minute block)
- OpenAPI schema is available at `/api/schema` without authentication

### TypeScript/Frontend

- Use TypeScript with strict mode
- Use shadcn-ui components from `@/components/ui/`
- Use path aliases: `@/components`, `@/lib`, `@/hooks`
- Add `data-testid` attributes to elements that need E2E testing
- Use Tailwind CSS v4 for styling
- **Always use the `apiRequest`, `apiGet`, `apiPost`, `apiPut` helpers from `@/lib/api` for API calls** instead of manual `fetch` calls
- **Use the `FormMessage` component from `@/components/ui/form-message`** for displaying error and success messages
- **CRITICAL: Always translate error messages using `errorCode` from API responses** - check for `errorCode` on error object and translate with `t(\`errorCodes.\${errorCode}\`)` (see EditUserPage.tsx for example)
- **CRITICAL: Always ensure dark mode compatibility for all UI elements**:
  - Add `text-foreground` class to all text elements (headings, labels, spans, paragraphs, loading text, etc.)
  - Add `text-foreground` class to Inputs via className prop
  - Add `text-foreground` class to Buttons with ghost variant via className prop
  - Add `dark` class to DropdownMenuContent (like DialogContent has)
  - All form elements must have explicit text color classes for dark theme compatibility

## Testing Guidelines

We focus on UI-level testing using Playwright. The REST API is considered an internal implementation detail.

### Unit Tests (src/test)

Playwright tests should extend `PlaywrightTestBase` which provides:
- Automatic browser lifecycle management
- Trace recording for debugging
- Clean test structure without boilerplate

**CRITICAL Test Transaction Rules:**
- **ALL `@MicronautTest` classes MUST have `transactional = false`** to avoid deadlocks
- **ALL database write operations MUST be wrapped in `testDatabaseSupport.inTransaction {}`**
- This ensures data is committed and visible to HTTP requests from browsers and other connections

**Important Playwright Testing Rules:**
- **Never use `page.waitForTimeout()`, `Thread.sleep()` or similar time-based waits** - these make tests flaky and hide real bugs
- **Always assume test failures are caused by bugs in your code**, not by timing issues or test environment problems
- **Always use Playwright's built-in auto-waiting assertions** like `assertThat().isVisible()`, `assertThat().containsText()`, etc.
- These assertions automatically retry until the condition is met or timeout occurs
- When verifying pagination or table content changes, check actual content (e.g., usernames) not just counts
- **When a test fails, investigate the root cause in your implementation** - check browser console, API responses (logged by PlaywrightTestBase), and component code
- PlaywrightTestBase automatically logs all AJAX requests and responses to help debug API-related issues
- **CRITICAL: When verifying lists, always use Playwright's `containsText()` with arrays** to ensure no extra items appear:
  - ❌ BAD: Check individual items with `isVisible()` and `not().isVisible()` - this misses extra items
  - ❌ BAD: Use `allTextContents()` and compare manually - no auto-waiting, causes flaky tests
  - ✅ GOOD: Use Playwright's built-in `assertThat(locator).containsText(arrayOf("item1", "item2"))`
  - Example: `val items = page.locator("[data-testid^='item-']"); assertThat(items).containsText(arrayOf("expected1", "expected2"))`
  - This pattern has auto-waiting behavior and catches implementation errors where extra items appear on the page
- **CRITICAL: Always verify that elements that should NOT be visible are actually not visible**:
  - When testing a state, verify both what SHOULD be visible AND what should NOT be visible
  - Example: When testing "no token" state, verify generate button IS visible AND token input is NOT visible
  - This prevents regressions where multiple states show at once
- **CRITICAL: Always verify database state in addition to UI state where applicable**:
  - After operations that modify the database (create, update, delete), verify the database state
  - Wrap database queries in `testDatabaseSupport.inTransaction {}` for proper transaction handling
  - Example: After clicking "Generate", verify token exists in database with correct properties
  - This ensures UI changes are properly persisted and prevents UI-only bugs
- **CRITICAL: Always wait for async UI operations to complete before reading UI state**:
  - When clicking a button that triggers an async API call (e.g., "show token"), wait for the UI to update before reading values
  - Use Playwright assertions to wait for the expected state (e.g., `assertThat(input).not().hasValue("masked")` before reading the actual value)
  - ❌ BAD: `showButton.click(); val value = input.inputValue()` - races with async API call
  - ✅ GOOD: `showButton.click(); assertThat(input).not().hasValue("••••••"); val value = input.inputValue()` - waits for async update
  - This prevents flaky tests where sometimes the old value is read before the async operation completes

Example:
```kotlin
@MicronautTest(transactional = false)  // CRITICAL: Disable test transactions
class MyPlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var userRepository: UserRepository
    
    @Inject
    lateinit var testDatabaseSupport: TestDatabaseSupport
    
    private lateinit var testUser: User

    @BeforeEach
    fun setupTestData() {
        // CRITICAL: Wrap in testDatabaseSupport.inTransaction to commit immediately
        // This makes the data visible to browser HTTP requests
        testUser = testDatabaseSupport.inTransaction {
            userRepository.save(
                User.create(
                    userName = "testuser",
                    passwordHash = BCrypt.hashpw("password", BCrypt.gensalt()),
                    greeting = "Test User",
                    isAdmin = false,
                    locale = Locale.ENGLISH,
                    languageCode = "en"
                )
            )
        }
    }

    @Test
    fun `my test`() {
        page.navigate("/login")
        // Now testUser is visible to login endpoint
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

### API Testing Guidelines

API tests should focus on **security** rather than business logic:
- Test that endpoints are properly protected (authentication/authorization)
- Test that users cannot bypass UI restrictions
- Business logic verification is done through Playwright UI tests
-  **Note**: Security enforcement is validated comprehensively in E2E tests with production Docker images. API endpoint tests use HTTP client to verify security at the API level (see UserAdminResourceTest for examples).

Example:
```kotlin
@Test
fun `should prevent self-deletion via API`() {
    // Test business rule that must not be bypassable
    // This is validated in Playwright tests which run against production-like environment
}
```

## Database

- Use Flyway for database migrations (place in `src/main/resources/db/migration/`)
- Use JDBI for database access with Kotlin extensions
- PostgreSQL is the target database
- **Testing**: Use Testcontainers JDBC URL (`jdbc:tc:postgresql:17:///test`) for automatic database provisioning in tests. The Micronaut Test Resources plugin is NOT used as it's redundant with Testcontainers JDBC.
