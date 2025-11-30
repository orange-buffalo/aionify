# GitHub Copilot Instructions

This document provides instructions for GitHub Copilot when working on this repository.

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

- `src/main/kotlin/` - Main application code
- `src/test/kotlin/` - Test code
- `frontend/` - React frontend application
- `.github/workflows/` - CI/CD workflows
- `.github/actions/` - Custom GitHub Actions

## Testing Guidelines

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
