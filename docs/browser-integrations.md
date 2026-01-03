# Browser Integrations Guide

Aionify provides Tampermonkey scripts that allow you to start and stop time tracking directly from GitHub and Jira web pages. These scripts automatically detect the issue or pull request you're viewing and sync with your Aionify time entries.

## Features

- **Start/Stop time tracking** directly from GitHub issues, pull requests, and Jira issues
- **Automatic synchronization** - polls every 10 seconds to keep button state in sync
- **Smart matching** - detects when you're already tracking time on the current issue/PR
- **Auto-fetch titles** - automatically retrieves issue/PR titles from GitHub and Jira APIs
- **Visual feedback** - uses the Aionify favicon for easy identification

## Prerequisites

1. **Tampermonkey browser extension** installed:
   - [Chrome/Edge](https://chrome.google.com/webstore/detail/tampermonkey/dhdgffkkebhmkfjojejmpbldmpobfkfo)
   - [Firefox](https://addons.mozilla.org/en-US/firefox/addon/tampermonkey/)
   - [Safari](https://apps.apple.com/us/app/tampermonkey/id1482490089)
   - [Opera](https://addons.opera.com/en/extensions/details/tampermonkey-beta/)

2. **Aionify API token** - see [Public API Guide](./public-api.md) for instructions on generating your token

## Installation

### GitHub Integration

1. Open the [GitHub integration script](https://raw.githubusercontent.com/orange-buffalo/aionify/main/supplements/integrations/aionify-github.user.js) in your browser
2. Tampermonkey should automatically detect the script and show an installation page
3. Click **Install**
4. Configure the script (see [Configuration](#configuration) section below)

### Jira Integration

1. Open the [Jira integration script](https://raw.githubusercontent.com/orange-buffalo/aionify/main/supplements/integrations/aionify-jira.user.js) in your browser
2. Tampermonkey should automatically detect the script and show an installation page
3. Click **Install**
4. Configure the script (see [Configuration](#configuration) section below)

**Note:** The script works with both Jira Cloud (*.atlassian.net) and Jira Server instances.

## Configuration

After installing a script, you need to configure it with your credentials:

1. Click the **Tampermonkey icon** in your browser toolbar
2. Click **Dashboard**
3. Find the installed script (e.g., "Aionify GitHub Integration")
4. Click the **Edit** icon (pencil)
5. Find the configuration section at the top of the script (around lines 10-20)
6. Update the following values:

### GitHub Script Configuration

```javascript
// Configuration - Update these values
const AIONIFY_BASE_URL = 'https://your-aionify-instance.com';  // Your Aionify URL (no trailing slash)
const AIONIFY_API_TOKEN = 'your-aionify-api-token-here';       // Your Aionify API token
```

### Jira Script Configuration

```javascript
// Configuration - Update these values
const AIONIFY_BASE_URL = 'https://your-aionify-instance.com';  // Your Aionify URL (no trailing slash)
const AIONIFY_API_TOKEN = 'your-aionify-api-token-here';       // Your Aionify API token
```

7. Click **File > Save** (or press Ctrl+S / Cmd+S)

## Usage

Once installed and configured, the scripts will automatically add menu commands when you visit GitHub or Jira pages:

### Accessing the Menu

1. Navigate to a GitHub issue/PR or Jira issue page
2. Click the **Tampermonkey icon** in your browser toolbar
3. Look for the menu item: **"⏱️ Start Time Tracking"** or **"⏱️ Stop Time Tracking"**

### GitHub

The menu command appears on:
- **Issue pages** (e.g., `https://github.com/owner/repo/issues/123`)
- **Pull request pages** (e.g., `https://github.com/owner/repo/pull/456`)

### Jira

The menu command appears on:
- **Issue pages** (e.g., `https://your-company.atlassian.net/browse/PROJ-123`)

### Menu Behavior

- **⏱️ Start Time Tracking** - Click to start tracking time on the current issue/PR
  - Automatically fetches the issue/PR title from the page
  - Creates a time entry with metadata matching the current page
  - If another entry is active, it will be stopped first

- **⏱️ Stop Time Tracking** - Click to stop tracking time on the current issue/PR
  - Only appears when you're actively tracking the current issue/PR
  - Stops the active time entry

The menu command automatically updates every 10 seconds to reflect your current tracking state.

## Automatic Updates

The scripts are configured to automatically check for updates from the GitHub repository. When a new version is released:

1. Tampermonkey will detect the update (usually within 24 hours)
2. You'll see a notification in the Tampermonkey menu
3. Click **Update** to install the latest version

Your configuration (API tokens, URLs) will be preserved during updates as Tampermonkey stores user modifications separately from the script code.

### Manual Update Check

To manually check for updates:

1. Click the **Tampermonkey icon**
2. Click **Dashboard**
3. Click the **Check for updates** tab
4. Click **Check for updates** button

## Metadata Format

The scripts create metadata entries in the following format:

- **GitHub Issues:** `gitHubIssue=owner/repo/number` (e.g., `gitHubIssue=facebook/react/12345`)
- **GitHub Pull Requests:** `gitHubPR=owner/repo/number` (e.g., `gitHubPR=facebook/react/12345`)
- **Jira Issues:** `jiraIssue=ISSUE-KEY` (e.g., `jiraIssue=PROJ-123`)

This metadata is used to match time entries to the pages you're viewing.

## Troubleshooting

### Menu command doesn't appear

1. **Check Tampermonkey is enabled:**
   - Click the Tampermonkey icon
   - Ensure the extension is enabled (not grayed out)
   - Ensure the script is enabled (check mark next to script name)

2. **Check you're on a supported page:**
   - For GitHub: must be an issue or pull request page
   - For Jira: must be an issue page (browse view)
   - For Jira Cloud: URLs like `https://*.atlassian.net/browse/PROJ-123`
   - For Jira Server: URLs like `https://yourcompany.com/jira/browse/PROJ-123`

3. **Refresh the page:**
   - The script may need a page refresh to initialize
   - Try reloading the page (F5 or Ctrl+R / Cmd+R)

3. **Refresh the page:**
   - The script may need a page refresh to initialize
   - Try reloading the page (F5 or Ctrl+R / Cmd+R)

4. **Check browser console for errors:**
   - Press F12 to open developer tools
   - Click the "Console" tab
   - Look for error messages starting with "[Aionify]"
   - **Note:** Debug logging is enabled to help with troubleshooting. Enable "Verbose" level in console to see debug messages.

### Menu shows wrong state

1. **Wait 10 seconds** - the script polls every 10 seconds for updates
2. **Check your configuration:**
   - Ensure `AIONIFY_BASE_URL` is correct (no trailing slash)
   - Ensure `AIONIFY_API_TOKEN` is valid
3. **Check API connectivity:**
   - Open your Aionify instance in a browser
   - Ensure you can access `https://your-aionify-instance.com/api/schema`

### "Failed to start/stop time entry" error

1. **Check API token is valid:**
   - Log in to Aionify
   - Go to Settings > API Access Token
   - Regenerate the token if needed
   - Update the script configuration with the new token

2. **Check rate limiting:**
   - If you see HTTP 429 errors, you may have hit the rate limit
   - Wait 10 minutes and try again

### Title not showing correctly

The scripts extract issue/PR titles directly from the page. If the title is not appearing:

1. **Refresh the page** - the title element may not have loaded yet
2. **Wait a few seconds** - the script polls every 10 seconds and will retry
3. **Check browser console** - look for debug messages about title extraction
4. **Report an issue** - if the GitHub/Jira UI has changed, the selectors may need updating

## Security Considerations

**Important:** The Tampermonkey scripts store your Aionify API token in plain text in the browser. Follow these security best practices:

1. **Only install scripts from trusted sources** - verify the script source before installation
2. **Keep tokens secure** - don't share your configured scripts with others
3. **Use dedicated API tokens** - create a separate Aionify token for browser integrations
4. **Rotate tokens regularly** - regenerate your Aionify token periodically
5. **Revoke unused tokens** - delete the token when you stop using the integration
6. **Use HTTPS only** - ensure your Aionify instance uses HTTPS

## Advanced Customization

### Changing poll interval

To change how often the menu updates (default is 10 seconds):

1. Edit the script in Tampermonkey dashboard
2. Find the `POLL_INTERVAL` constant (around line 15)
3. Change the value (in milliseconds): `const POLL_INTERVAL = 10000;`
4. Save the script

## Uninstallation

To remove a script:

1. Click the **Tampermonkey icon**
2. Click **Dashboard**
3. Find the script you want to remove
4. Click the **trash icon** (🗑️)
5. Confirm deletion
Your Aionify API token will be removed along with the script.

## Further Reading

- [Public API Guide](./public-api.md) - Complete API documentation
- [Tampermonkey Documentation](https://www.tampermonkey.net/documentation.php)
- [GitHub API Documentation](https://docs.github.com/en/rest)
- [Jira API Documentation](https://developer.atlassian.com/cloud/jira/platform/rest/v3/)
