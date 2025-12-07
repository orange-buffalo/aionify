# Deployment Guide

This guide explains how to deploy Aionify in a production environment.

## Container Image

Aionify is distributed as a container image available from GitHub Container Registry:

```
ghcr.io/orange-buffalo/aionify:latest
```

## Prerequisites

- Docker or any OCI-compatible container runtime
- PostgreSQL 15+ database

## Database Setup

Aionify requires a PostgreSQL database. Create a database for the application:

```sql
CREATE DATABASE aionify;
CREATE USER aionify WITH PASSWORD 'your-secure-password';
GRANT ALL PRIVILEGES ON DATABASE aionify TO aionify;
```

## Configuration

Configure the application using environment variables:

| Variable | Description | Required |
|----------|-------------|----------|
| `AIONIFY_DB_URL` | JDBC connection URL for PostgreSQL | Yes |
| `AIONIFY_DB_USERNAME` | Database username | Yes |
| `AIONIFY_DB_PASSWORD` | Database password | Yes |

### Database URL Format

```
jdbc:postgresql://hostname:5432/database_name
```

Example:
```
jdbc:postgresql://db.example.com:5432/aionify
```

## Running with Docker

```bash
docker run -d \
  --name aionify \
  -p 8080:8080 \
  -e AIONIFY_DB_URL="jdbc:postgresql://db.example.com:5432/aionify" \
  -e AIONIFY_DB_USERNAME="aionify" \
  -e AIONIFY_DB_PASSWORD="your-secure-password" \
  ghcr.io/orange-buffalo/aionify:latest
```

The application will be available at `http://localhost:8080`.

## Running with Docker Compose

A reference Docker Compose configuration is available in the repository at [`src/test/resources/docker-compose-e2e.yml`](https://github.com/orange-buffalo/aionify/blob/main/src/test/resources/docker-compose-e2e.yml).

For production use, you have two options:

### Option 1: Use the reference file with environment variable

Copy the reference file and set the `AIONIFY_IMAGE` environment variable:

```bash
# Set the image
export AIONIFY_IMAGE=ghcr.io/orange-buffalo/aionify:latest

# Or inline with the command
AIONIFY_IMAGE=ghcr.io/orange-buffalo/aionify:latest docker compose -f docker-compose-e2e.yml up -d
```

### Option 2: Create a production compose file with explicit image

Create your own `docker-compose.yml` file with explicit image tag:

```yaml
services:
  aionify:
    image: ghcr.io/orange-buffalo/aionify:latest
    ports:
      - "8080:8080"
    environment:
      AIONIFY_DB_URL: jdbc:postgresql://db:5432/aionify
      AIONIFY_DB_USERNAME: aionify
      AIONIFY_DB_PASSWORD: your-secure-password  # Change this!
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:17
    environment:
      POSTGRES_DB: aionify
      POSTGRES_USER: aionify
      POSTGRES_PASSWORD: your-secure-password  # Change this!
    volumes:
      - postgres_data:/var/lib/postgresql/data  # Persist data across restarts
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U aionify -d aionify"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:  # Named volume for database persistence
```

**Important**: 
- Change `your-secure-password` to a strong, unique password
- The `postgres_data` volume ensures database data persists across container restarts

Start the application:

```bash
docker compose up -d
```

## Database Migrations

Aionify uses Flyway for database migrations. Migrations are applied automatically when the application starts. No manual migration steps are required.

## Authentication

Aionify uses JWT (JSON Web Tokens) for authentication. JWT signing keys are automatically generated at application startup, which means:

- Users will need to log in again after the application restarts
- The application is designed for single-instance deployments

## Default Admin User

On first startup, if no admin user exists in the database, Aionify automatically creates a default admin user:

- **Username**: `sudo`
- **Password**: Generated randomly

The generated password is printed to the application output (stdout) on startup:

```
============================================================
DEFAULT ADMIN CREATED
Username: sudo
Password: <random-generated-password>
Please change this password after first login!
============================================================
```

**Important**: 
- Check the container logs immediately after first startup to retrieve the password
- The password is only displayed once and is not stored anywhere
- Change the password after first login for security

To view the password in Docker:
```bash
docker logs aionify 2>&1 | grep -A5 "DEFAULT ADMIN CREATED"
```

## Network Configuration

### Exposed Ports

| Port | Protocol | Description |
|------|----------|-------------|
| 8080 | HTTP | Application web interface and API |

### Reverse Proxy

For production deployments, it's recommended to use a reverse proxy (like nginx, Traefik, or Caddy) to:
- Terminate TLS/SSL
- Handle load balancing
- Provide additional security headers

Example nginx configuration:

```nginx
server {
    listen 443 ssl http2;
    server_name aionify.example.com;

    ssl_certificate /path/to/certificate.pem;
    ssl_certificate_key /path/to/private-key.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Health Checks

The application is healthy when it successfully connects to the database and completes startup. Use the container's startup as an indicator of health, or implement a simple HTTP check to the root path.

## Troubleshooting

### Application won't start

1. **Check database connectivity**: Ensure the PostgreSQL server is accessible from the container
2. **Verify credentials**: Double-check `AIONIFY_DB_URL`, `AIONIFY_DB_USERNAME`, and `AIONIFY_DB_PASSWORD`
3. **Check logs**: `docker logs aionify` for error messages

### Cannot find default admin password

The password is only shown in logs during first startup. If you missed it and cannot log in:

1. Stop the application
2. Delete the admin user from the database:
   ```sql
   DELETE FROM users WHERE user_name = 'sudo';
   ```
3. Restart the application to regenerate a new admin user

### Database connection errors

Ensure the database URL format is correct:
```
jdbc:postgresql://hostname:port/database
```

Common issues:
- Missing `jdbc:postgresql://` prefix
- Incorrect hostname or port
- Database name doesn't exist
- User lacks permissions
