# Aionify Browser Integrations

This directory contains Tampermonkey scripts for integrating Aionify time tracking with popular web applications.

## Available Scripts

### aionify-engine.user.js

The core engine that provides common functionality for all Aionify integrations:

- API client for communicating with Aionify
- Button management and state synchronization
- Polling mechanism for active time entries
- Shared UI components

This script is automatically loaded by the integration scripts via `@require` directive.

### aionify-github.user.js

Integration for GitHub issues and pull requests:

- Automatically detects GitHub issue and PR pages
- Adds a Start/Stop button to the GitHub UI
- Fetches issue/PR titles from GitHub API
- Tracks time with metadata: `gitHubIssue=owner/repo/number` or `gitHubPR=owner/repo/number`

**Configuration required:**
- `AIONIFY_BASE_URL` - Your Aionify instance URL
- `AIONIFY_API_TOKEN` - Your Aionify API token
- `GITHUB_TOKEN` - GitHub personal access token (for API access)

### aionify-jira.user.js

Integration for Jira Cloud issues:

- Automatically detects Jira issue pages
- Adds a Start/Stop button to the Jira UI
- Fetches issue titles from Jira API
- Tracks time with metadata: `jiraIssue=ISSUE-KEY`

**Configuration required:**
- `AIONIFY_BASE_URL` - Your Aionify instance URL
- `AIONIFY_API_TOKEN` - Your Aionify API token
- `JIRA_BASE_URL` - Your Jira instance URL
- `JIRA_EMAIL` - Your Jira account email
- `JIRA_API_TOKEN` - Jira API token

## Installation

See the [Browser Integrations Guide](../../docs/browser-integrations.md) for detailed installation and configuration instructions.

## Development

### Script Structure

Each integration script follows this pattern:

1. **Configuration section** - User-editable constants for API credentials
2. **URL parsing** - Extracts relevant information from the current page
3. **API integration** - Fetches additional data (titles) from external APIs
4. **Configuration object** - Implements the interface expected by the engine
5. **Initialization** - Waits for page load and injects the button

### Adding a New Integration

To add support for a new platform:

1. Create a new `.user.js` file (e.g., `aionify-platform.user.js`)
2. Add the script header with proper metadata:
   ```javascript
   // ==UserScript==
   // @name         Aionify Platform Integration
   // @namespace    io.orangebuffalo.aionify
   // @version      1.0.0
   // @description  Description
   // @match        https://platform.com/pattern/*
   // @require      https://raw.githubusercontent.com/orange-buffalo/aionify/main/supplements/integrations/aionify-engine.user.js
   // @downloadURL  https://raw.githubusercontent.com/orange-buffalo/aionify/main/supplements/integrations/aionify-platform.user.js
   // @updateURL    https://raw.githubusercontent.com/orange-buffalo/aionify/main/supplements/integrations/aionify-platform.user.js
   // @grant        GM_xmlhttpRequest
   // ==/UserScript==
   ```
3. Implement the configuration object:
   ```javascript
   const config = {
       pollInterval: 10000,
       getMetadataForCurrentPage: function() {
           // Return array of metadata strings
           return ['platform=identifier'];
       },
       getTitleForCurrentPage: async function() {
           // Return formatted title string
           return 'IDENTIFIER Title from API';
       }
   };
   ```
4. Initialize the button:
   ```javascript
   const client = new window.Aionify.Client(BASE_URL, API_TOKEN);
   const button = new window.Aionify.Button(client, config);
   button.initialize((buttonElement) => {
       // Insert button into DOM
   });
   ```
5. Update the documentation

### Testing

To test a script locally during development:

1. Make your changes to the script file
2. In Tampermonkey, click "Dashboard"
3. Find your script and click "Edit"
4. Copy the entire content from your local file
5. Paste into the editor
6. Click "File" > "Save"
7. Navigate to a test page (e.g., GitHub issue)
8. Check the browser console for debug messages
9. Verify button appears and functions correctly

### Versioning

When making changes:

1. Update the `@version` in the script header
2. Follow semantic versioning:
   - Major (1.0.0 → 2.0.0): Breaking changes
   - Minor (1.0.0 → 1.1.0): New features
   - Patch (1.0.0 → 1.0.1): Bug fixes
3. Tampermonkey will automatically notify users of updates

## Troubleshooting

### Scripts not loading

- Check Tampermonkey is enabled
- Verify the `@match` patterns are correct
- Check browser console for errors

### Button not appearing

- Verify you're on a supported page
- Check the console for initialization messages
- Ensure the page DOM structure hasn't changed

### API requests failing

- Verify configuration values are correct
- Check CORS settings if running locally
- Review network tab in browser dev tools
- Check API token validity

## Security

**Important:** These scripts store API tokens in plain text in the browser. Follow security best practices:

- Only install from trusted sources (this repository)
- Keep tokens secure and rotate regularly
- Use dedicated API tokens for browser integrations
- Review script code before installation
- Revoke tokens when no longer needed

## License

Apache License 2.0 - See [LICENSE](../../LICENSE) file for details
