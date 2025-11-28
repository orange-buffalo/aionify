# Aionify

Self-hosted time tracking app

## Tech Stack

- **Backend**: Quarkus with Kotlin
- **Frontend**: React with shadcn-ui components
- **Build**: Bun for frontend, Gradle for backend
- **Testing**: Playwright for E2E tests

## Prerequisites

- Java 17+
- Gradle 8.10+ (wrapper included)
- Bun 1.0+ (install from https://bun.sh)
- Node.js 18+ (for Playwright browser management)

## Development

### Build the project

```bash
./gradlew build
```

This will:
1. Install frontend dependencies with Bun
2. Build the React frontend with Bun
3. Compile the Kotlin backend

### Run in development mode

```bash
./gradlew quarkusDev
```

The application will be available at http://localhost:8080

### Run tests

```bash
./gradlew test
```

This runs both:
- REST API tests
- Playwright E2E tests for the frontend

## Project Structure

```
├── frontend/           # React frontend
│   ├── src/
│   │   ├── components/ # React components (including shadcn-ui)
│   │   ├── lib/        # Utility functions
│   │   ├── App.tsx     # Main application component
│   │   └── main.tsx    # Entry point
│   ├── build.ts        # Bun build script
│   ├── bun.lock        # Bun lock file
│   └── package.json    # Frontend dependencies
├── src/
│   ├── main/
│   │   ├── kotlin/     # Kotlin backend code
│   │   └── resources/  # Configuration files
│   └── test/
│       └── kotlin/     # Test files (including Playwright tests)
└── build.gradle.kts    # Gradle configuration
```

## License

Apache License 2.0
