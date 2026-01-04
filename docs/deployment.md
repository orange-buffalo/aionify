# Deployment Guide

This guide explains how to deploy Aionify in a production environment.

## Container Image

Aionify is distributed as a container image available from [GitHub Container Registry](https://github.com/orange-buffalo/aionify/pkgs/container/aionify/versions):

```
ghcr.io/orange-buffalo/aionify:<latest stable version>
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
| `AIONIFY_JWT_SECRET` | Signing secret for JWT (HS256). Must be provided; empty values will cause token generation to fail. Provide a strong base64-encoded secret (32+ bytes / 256 bits) for production. | Yes |

### Database URL Format

```
jdbc:postgresql://hostname:5432/database_name
```

Example:
```
jdbc:postgresql://db.example.com:5432/aionify
```

### JWT signing key

Aionify requires a JWT signing secret. You must provide a non-empty secret via the `AIONIFY_JWT_SECRET` environment variable; an empty value is not supported and will cause token generation to fail. Choose a secret with sufficient entropy (we recommend at least 32 bytes / 256 bits, encoded as base64).

Generate a secure secret locally:

```bash
# OpenSSL: 32 bytes -> base64
openssl rand -base64 32

# Or from /dev/urandom
head -c 32 /dev/urandom | base64
```

Set the variable in your environment, container runtime, or orchestration platform. The examples in the "Running with Docker" and "Running with Docker Compose" sections below show where to provide `AIONIFY_JWT_SECRET`.

Security notes:
- Store `AIONIFY_JWT_SECRET` in a secrets manager (Vault, AWS Secrets Manager, etc.) and avoid committing it to version control.
- Rotating the secret will invalidate all existing tokens; plan rotations carefully.

## Running with Docker

```bash
docker run -d \
  --name aionify \
  -p 8080:8080 \
  -e AIONIFY_DB_URL="jdbc:postgresql://db.example.com:5432/aionify" \
  -e AIONIFY_DB_USERNAME="aionify" \
  -e AIONIFY_DB_PASSWORD="your-secure-password" \
  -e AIONIFY_JWT_SECRET="your-base64-encoded-32-byte-secret" \
  ghcr.io/orange-buffalo/aionify:0.1.0
```

The application will be available at `http://localhost:8080`.

## Running with Docker Compose

A reference Docker Compose configuration is available in the repository at [`src/test/resources/docker-compose-e2e.yml`](https://github.com/orange-buffalo/aionify/blob/main/src/test/resources/docker-compose-e2e.yml).

For production use, copy the reference file, uncomment relevant parts, and set the environment variables before starting the stack:

```bash
# Set the environment variables, then run compose
export AIONIFY_DB_URL="jdbc:postgresql://db.example.com:5432/aionify"
export AIONIFY_DB_USERNAME="aionify"
export AIONIFY_DB_PASSWORD="your-secure-password"
export AIONIFY_JWT_SECRET="your-base64-encoded-32-byte-secret"
export AIONIFY_IMAGE=ghcr.io/orange-buffalo/aionify:0.1.0
docker compose -f docker-compose.yml up -d
```

**Important**: 
- Change password to a strong, unique password
- The `postgres_data` volume ensures database data persists across container restarts

## Database Migrations

Aionify uses Flyway for database migrations. Migrations are applied automatically when the application starts. No manual migration steps are required.

## Authentication

Aionify uses JWT (JSON Web Tokens) for authentication. The application requires a valid, non-empty JWT signing secret provided via `AIONIFY_JWT_SECRET`. If the secret is not provided or is empty, token generation and authentication will fail. Ensure `AIONIFY_JWT_SECRET` is set before starting the application in production.

If you want tokens to remain valid across restarts (or run multiple instances), set `AIONIFY_JWT_SECRET` to a stable, high-entropy secret as described above.

## User Administration

After deployment, you'll need to set up user accounts. See the [User Administration Guide](administration.md) for:

- Accessing the default admin account
- Creating and managing users
- User onboarding with activation tokens
- Password management and security best practices

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

Note about Server-Sent Events (SSE): Aionify uses SSE to deliver realtime updates (the event stream is exposed at `/api-ui/time-log-entries/events`). When you front the application with nginx (or other proxies), you must keep SSE connections open and disable buffering so events are delivered immediately. The nginx snippet below shows the required proxy settings (HTTP/1.1, no buffering, and an increased read timeout). If you use a different proxy, apply equivalent settings for SSE support.

Example nginx configuration:

```nginx
server {
    listen 443 ssl http2;
    server_name aionify.example.com;

    ssl_certificate /path/to/certificate.pem;
    ssl_certificate_key /path/to/private-key.pem;

    # SSE support
    location /api-ui/time-log-entries/events {
        proxy_pass http://localhost:8080;
        
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding off;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 10m;
    }

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

See the [User Administration Guide](administration.md#cannot-find-default-admin-password) for recovery steps.

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
