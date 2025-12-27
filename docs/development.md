# Development Guide

This guide explains how to set up a local development environment for Aionify.

## Prerequisites

- **Java 21+** - Required for running the backend
- **Gradle 8.10+** - Build tool (wrapper included in repository)
- **Bun 1.0+** - For building the frontend ([install from bun.sh](https://bun.sh))
- **Docker** - For running PostgreSQL via Testcontainers
- **Node.js 18+** - Required for Playwright browser management (E2E tests)

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/orange-buffalo/aionify.git
cd aionify
```

### 2. Build the project

```bash
./gradlew build --build-cache --console=plain
```

This will:
1. Install frontend dependencies with Bun
2. Build the React frontend
3. Compile the Kotlin backend
4. Run all tests (including Playwright E2E tests)

### 3. Run in development mode

```bash
./gradlew run
```

The application will be available at http://localhost:8080

**Note**: For development, you'll need a running PostgreSQL database. See "Using an External Database" section below for setup instructions.

## Development with Persistent Database

For development, you can use Docker to run a PostgreSQL database:

### Using Docker for PostgreSQL

1. Start PostgreSQL (via Docker Compose):
   ```bash
   docker compose -f docker-compose.dev.yml up -d
   ```

   Create `docker-compose.dev.yml`:
   ```yaml
   services:
     db:
       image: postgres:17
       ports:
         - "5432:5432"
       environment:
         POSTGRES_DB: aionify_dev
         POSTGRES_USER: aionify
         POSTGRES_PASSWORD: aionify
       volumes:
         - aionify_dev_data:/var/lib/postgresql/data

   volumes:
     aionify_dev_data:
   ```

2. Run the application with the database:
   ```bash
   ./gradlew run
   ```

   The application will use the default database connection settings (localhost:5432).
   To customize, set environment variables:
   ```bash
   AIONIFY_DB_URL=jdbc:postgresql://localhost:5432/aionify_dev \
   AIONIFY_DB_USERNAME=aionify \
   AIONIFY_DB_PASSWORD=aionify \
   ./gradlew run
   ```

## Project Structure

```
├── frontend/                    # React frontend application
│   ├── src/
│   │   ├── components/         # React components
│   │   │   └── ui/             # shadcn-ui components
│   │   ├── lib/                # Utility functions
│   │   ├── App.tsx             # Main application component
│   │   └── main.tsx            # Entry point
│   ├── build.ts                # Bun build script
│   └── package.json            # Frontend dependencies
├── src/
│   ├── main/
│   │   ├── kotlin/             # Kotlin backend code
│   │   │   └── io/orangebuffalo/aionify/
│   │   │       ├── config/     # Configuration classes
│   │   │       └── domain/     # Domain models and repositories
│   │   └── resources/
│   │       ├── application.properties  # Application configuration
│   │       └── db/migration/           # Flyway migrations
│   └── test/
│       └── kotlin/             # Test files (including Playwright tests)
├── docs/                       # Documentation
└── build.gradle.kts            # Gradle build configuration
```

## Running Tests

### All tests
```bash
./gradlew test
```

### Specific test class
```bash
./gradlew test --tests "io.orangebuffalo.aionify.FrontendPlaywrightTest"
```

### Install Playwright browsers
Playwright browsers are installed automatically during the build. To install manually:
```bash
./gradlew installPlaywrightBrowsers
```

## Common Development Tasks

### Frontend hot reload

For faster frontend development:

1. In one terminal, run the backend:
   ```bash
   ./gradlew run
   ```

2. In another terminal, rebuild frontend on changes:
   ```bash
   cd frontend
   bun run build
   ```

### Database migrations

Migrations are stored in `src/main/resources/db/migration/` and follow Flyway naming conventions:
- `V{version}__{description}.sql` for versioned migrations

Example: `V3__add_projects_table.sql`

Migrations are applied automatically on application startup.

### Adding new dependencies

Backend dependencies are managed in `build.gradle.kts`. Frontend dependencies use Bun:

```bash
cd frontend
bun add <package-name>
```

## Coding Guidelines

For detailed coding conventions and standards, see the [GitHub Copilot Instructions](../.github/copilot-instructions.md) file. This file is kept up-to-date with the latest project conventions.

### Code Formatting

The project uses automatic code formatters to maintain consistent code style:

**Before committing any code, always run:**
```bash
./gradlew format
```

This command formats both Kotlin code (using ktlint) and frontend code (using Prettier).

**To check if code is properly formatted:**
```bash
./gradlew check
```

The `check` task runs all formatting checks and will fail if code is not properly formatted. This task is also run in CI.

**Individual formatters:**
- Format Kotlin only: `./gradlew ktlintFormat`
- Format frontend only: `./gradlew prettierFormat`
- Check Kotlin formatting: `./gradlew ktlintCheck`
- Check frontend formatting: `./gradlew prettierCheck`

## Troubleshooting

### Testcontainers issues

If you see errors about Docker not being available:
1. Ensure Docker is running
2. On Linux, ensure your user is in the `docker` group

### Playwright browser not found

Run:
```bash
./gradlew installPlaywrightBrowsers
```

### Port already in use

If port 8080 is in use, specify a different port via environment variable:
```bash
MICRONAUT_SERVER_PORT=8081 ./gradlew run
```

### Frontend build fails

Try clearing the node_modules and reinstalling:
```bash
cd frontend
rm -rf node_modules
bun install
```
