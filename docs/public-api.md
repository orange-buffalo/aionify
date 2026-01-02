# Public API Guide

Aionify provides a public API for programmatic access to time tracking functionality. This guide explains how to authenticate and use the API.

## API Schema

The complete OpenAPI schema is available at:

```
https://your-aionify-instance.com/api/schema
```

The schema endpoint does not require authentication and provides detailed documentation of all available endpoints, request/response formats, and error codes.

## Authentication

All API endpoints (except `/api/schema`) require authentication using Bearer tokens.

### Generating an API Token

1. Log in to your Aionify account
2. Click on the **Settings** icon (⚙️) in the navigation bar
3. Scroll down to the **API Access Token** section
4. Click the **Generate Token** button (or **Regenerate Token** if you already have one)

<!-- ![API Token Generation](./images/api-token-generation.png) -->

5. Click the **eye icon** (👁️) to reveal the token
6. Click the **copy icon** (📋) to copy the token to your clipboard

<!-- ![API Token Display](./images/api-token-display.png) -->

> **Note:** Screenshots will be added in a future update. For now, follow the text instructions above.

**Important:**
- Store the token securely - it provides full access to your account
- Each user can have only one active API token
- Regenerating the token will invalidate the previous one
- If you delete the token, all API access will be denied

### Using the Token

Include the token in the `Authorization` header of your HTTP requests:

```bash
curl -H "Authorization: Bearer YOUR_API_TOKEN" \
  https://your-aionify-instance.com/api/time-log-entries/active
```

## Rate Limiting

To protect against brute force attacks, the API implements rate limiting:

- **10 failed authentication attempts** from the same IP address will result in a **10-minute block**
- Successful authentication clears the failed attempt counter
- When an IP is blocked, the API returns HTTP **429 (Too Many Requests)**

## Available Endpoints

### Get Active Time Log Entry

```http
GET /api/time-log-entries/active
```

Returns the currently active time log entry, or 404 if no entry is active.

**Response (200 OK):**
```json
{
  "title": "Working on feature X",
  "metadata": ["project:aionify", "gitHubPR=owner/repo/123"]
}
```

**Response (404 Not Found):**
```json
{
  "error": "No active time log entry",
  "errorCode": "NO_ACTIVE_ENTRY"
}
```

### Start Time Log Entry

```http
POST /api/time-log-entries/start
Content-Type: application/json

{
  "title": "Working on feature X",
  "metadata": ["project:aionify", "gitHubPR=owner/repo/123"]
}
```

Starts a new time log entry. If there's already an active entry, it will be automatically stopped before starting the new one.

**Request body:**
- `title` (required): Title of the time log entry (max 1000 characters)
- `metadata` (optional): Array of metadata strings

**Response (200 OK):**
```json
{
  "title": "Working on feature X",
  "metadata": ["project:aionify", "gitHubPR=owner/repo/123"]
}
```

### Stop Active Time Log Entry

```http
POST /api/time-log-entries/stop
```

Stops the currently active time log entry. If there's no active entry, the operation succeeds with no changes.

**Response (200 OK):**
```json
{
  "message": "Entry stopped",
  "stopped": true
}
```

Or when no entry was active:
```json
{
  "message": "No active entry",
  "stopped": false
}
```

## Error Responses

All API endpoints may return these error responses:

### 401 Unauthorized

Missing or invalid API token:

```json
{
  "error": "Invalid API token"
}
```

### 400 Bad Request

Invalid request body (e.g., blank title):

```json
{
  "error": "Title cannot be blank",
  "errorCode": "VALIDATION_ERROR"
}
```

### 429 Too Many Requests

IP blocked due to too many failed authentication attempts:

```json
{
  "error": "Too many failed authentication attempts. Please try again later."
}
```

## Metadata Format

The `metadata` field accepts an array of strings in key-value format. While you can use any format, the following conventions are recommended for browser integrations:

- **GitHub Issues:** `gitHubIssue=owner/repo/number` (e.g., `gitHubIssue=facebook/react/12345`)
- **GitHub Pull Requests:** `gitHubPR=owner/repo/number` (e.g., `gitHubPR=facebook/react/12345`)
- **Jira Issues:** `jiraIssue=ISSUE-KEY` (e.g., `jiraIssue=PROJ-123`)
- **Custom Projects:** `project:name` (e.g., `project:aionify`)
- **Custom Tags:** `tag:value` (e.g., `tag:urgent`)

These conventions enable browser integration scripts (see [Browser Integrations Guide](./browser-integrations.md)) to automatically match time entries to the pages you're viewing.

## Examples

### Start tracking work on a GitHub issue

```bash
curl -X POST \
  -H "Authorization: Bearer YOUR_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title": "facebook/react/12345 Fix memory leak in hooks", "metadata": ["gitHubIssue=facebook/react/12345"]}' \
  https://your-aionify-instance.com/api/time-log-entries/start
```

### Check if currently tracking time

```bash
curl -H "Authorization: Bearer YOUR_API_TOKEN" \
  https://your-aionify-instance.com/api/time-log-entries/active
```

### Stop the active entry

```bash
curl -X POST \
  -H "Authorization: Bearer YOUR_API_TOKEN" \
  https://your-aionify-instance.com/api/time-log-entries/stop
```

## Security Best Practices

1. **Never commit tokens to source control** - use environment variables or secure configuration
2. **Use HTTPS only** - never send tokens over unencrypted connections
3. **Rotate tokens regularly** - regenerate tokens periodically for security
4. **Monitor token usage** - watch for unexpected API calls in server logs
5. **Delete unused tokens** - remove tokens when they're no longer needed

## Further Reading

- [Browser Integrations Guide](./browser-integrations.md) - Tampermonkey scripts for GitHub and Jira
- [OpenAPI Schema](/api/schema) - Complete API documentation
