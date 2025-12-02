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
./gradlew quarkusDev
```

The application will be available at http://localhost:8080

**Note**: Quarkus Dev Services automatically starts a PostgreSQL container via Testcontainers. No database setup is required for development.

## Development with Persistent Database

By default, when using `quarkusDev`, the database container is stopped when Quarkus stops, and all data is lost. To persist data between development sessions:

### Enable Container Reuse

1. Create or edit `~/.testcontainers.properties`:
   ```properties
   testcontainers.reuse.enable=true
   ```

2. Run with reuse enabled:
   ```bash
   ./gradlew quarkusDev -Dquarkus.datasource.devservices.reuse=true
   ```

The PostgreSQL container will now persist between application restarts, preserving your test data.

To stop and remove the reusable container when no longer needed:
```bash
docker ps -a --filter "label=org.testcontainers.reuse=true" -q | xargs -r docker rm -f
```

### Using External PostgreSQL

Alternatively, you can use a local PostgreSQL instance:

1. Start PostgreSQL (e.g., via Docker Compose):
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

2. Run Quarkus with the external database:
   ```bash
   ./gradlew quarkusDev \
     -Dquarkus.datasource.devservices.enabled=false \
     -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/aionify_dev \
     -Dquarkus.datasource.username=aionify \
     -Dquarkus.datasource.password=aionify
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

In development mode (`quarkusDev`), frontend changes require rebuilding. For faster frontend development:

1. In one terminal, run the backend:
   ```bash
   ./gradlew quarkusDev
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

If port 8080 is in use, specify a different port:
```bash
./gradlew quarkusDev -Dquarkus.http.port=8081
```

### Frontend build fails

Try clearing the node_modules and reinstalling:
```bash
cd frontend
rm -rf node_modules
bun install
```
