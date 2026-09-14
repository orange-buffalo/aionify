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

### Creating an API Token

1. Log in to your Aionify account
2. Click on the **Settings** icon (⚙️) in the navigation bar
3. Scroll down to the **API Access Tokens** section
4. Enter a name for the token, e.g. the integration that will use it (`GitHub userscript`), and click **Create Token**
5. Click the **eye icon** (👁️) next to the token to reveal it
6. Click the **copy icon** (📋) to copy the token to your clipboard

**Important:**
- Store tokens securely - each token provides full access to your account
- Create a separate token for each integration, so that you can revoke access for one integration without affecting the others
- Each user can have up to 20 tokens; token names must be unique per user
- Regenerating a token invalidates only that token
- Deleting a token immediately denies API access for everything that uses it

OS-specific wrappers can also request a token from within the app, with the user's consent - see the [OS Wrappers Guide](./os-wrappers.md).

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

## API Endpoints

The complete API reference is available in the OpenAPI schema at `/api/schema`.

## Event Stream

Integrations can receive real-time updates about time log entries instead of polling. The stream includes changes made in the web UI, via the API or by other integrations.

```bash
curl -N -H "Authorization: Bearer YOUR_API_TOKEN" \
  https://your-aionify-instance.com/api/time-log-entries/events
```

The endpoint uses [Server-Sent Events](https://html.spec.whatwg.org/multipage/server-sent-events.html):

- A `heartbeat` event is sent right after connecting and then every 30 seconds. Reconnect if no heartbeat is received within 45 seconds.
- Each change is sent as a default (unnamed) event whose data is a JSON object with the change `type` and the `entry` state after the change (same format as other time log entry endpoints):

```
event: heartbeat
data: heartbeat

data: {"type":"ENTRY_STARTED","entry":{"startTime":"2024-01-15T10:30:00Z","title":"Working on feature X","metadata":["project:aionify"]}}
```

Fields without a value (e.g. `endTime` of an active entry or empty `tags`) may be omitted from event data.

Current event types are `ENTRY_STARTED` and `ENTRY_STOPPED` (starting an entry while another one is active produces both). Events that happen while a client is disconnected are not replayed: after (re)connecting, load the state you need, e.g. via `GET /api/time-log-entries/active`.

## API Stability

The public API evolves in a backwards-compatible way:

- New endpoints, fields and event types may be added at any time - clients must ignore fields and event types they do not know
- Existing fields and event types are not removed, renamed or changed in meaning; such changes would be introduced as new endpoints

## Metadata Format

The `metadata` field in API requests accepts an array of strings. The following conventions are used by browser integrations to match time entries to web pages:

- **GitHub Issues:** `gitHubIssue=owner/repo/number` (e.g., `gitHubIssue=facebook/react/12345`)
- **GitHub Pull Requests:** `gitHubPR=owner/repo/number` (e.g., `gitHubPR=facebook/react/12345`)
- **Jira Issues:** `jiraIssue=ISSUE-KEY` (e.g., `jiraIssue=PROJ-123`)
- **Custom Projects:** `project:name` (e.g., `project:aionify`)
- **Custom Tags:** `tag:value` (e.g., `tag:urgent`)

These conventions enable browser integration scripts (see [Browser Integrations Guide](./browser-integrations.md)) to automatically match time entries to the pages you're viewing.

You can use any metadata format for custom integrations.

## Examples

For detailed API usage examples including request/response formats, see the OpenAPI schema at `/api/schema`.

## Security Best Practices

1. **Never commit tokens to source control** - use environment variables or secure configuration
2. **Use HTTPS only** - never send tokens over unencrypted connections
3. **Rotate tokens regularly** - regenerate tokens periodically for security
4. **Monitor token usage** - watch for unexpected API calls in server logs
5. **Delete unused tokens** - remove tokens when they're no longer needed

## Further Reading

- [Browser Integrations Guide](./browser-integrations.md) - Tampermonkey scripts for GitHub and Jira
- [OS Wrappers Guide](./os-wrappers.md) - Building native apps that host Aionify
- [OpenAPI Schema](/api/schema) - Complete API documentation
