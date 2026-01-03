# Deployment Guide

This guide explains how to deploy Aionify in a production environment.

## Container Image

Aionify is distributed as a container image available from GitHub Container Registry:

```
ghcr.io/orange-buffalo/aionify:main
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
  ghcr.io/orange-buffalo/aionify:main
```

The application will be available at `http://localhost:8080`.

## Running with Docker Compose

A reference Docker Compose configuration is available in the repository at [`src/test/resources/docker-compose-e2e.yml`](https://github.com/orange-buffalo/aionify/blob/main/src/test/resources/docker-compose-e2e.yml).

For production use, copy the reference file and set the `AIONIFY_IMAGE` environment variable:

```bash
# Set the image
export AIONIFY_IMAGE=ghcr.io/orange-buffalo/aionify:main

# Or inline with the command
AIONIFY_IMAGE=ghcr.io/orange-buffalo/aionify:main docker compose -f docker-compose-e2e.yml up -d
```

**Important**: 
- Change password to a strong, unique password
- The `postgres_data` volume ensures database data persists across container restarts

## Database Migrations

Aionify uses Flyway for database migrations. Migrations are applied automatically when the application starts. No manual migration steps are required.

## Authentication

Aionify uses JWT (JSON Web Tokens) for authentication. JWT signing keys are automatically generated at application startup, which means:

- Users will need to log in again after the application restarts
- The application is designed for single-instance deployments

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
