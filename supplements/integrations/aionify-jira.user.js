// ==UserScript==
// @name         Aionify Jira Integration
// @namespace    io.orangebuffalo.aionify
// @version      1.0.0
// @description  Start/stop Aionify time tracking from Jira issues
// @author       Aionify Contributors
// @license      Apache-2.0
// @match        https://*.atlassian.net/browse/*
// @match        https://*/jira/browse/*
// @require      https://raw.githubusercontent.com/orange-buffalo/aionify/main/supplements/integrations/aionify-engine.user.js
// @downloadURL  https://raw.githubusercontent.com/orange-buffalo/aionify/main/supplements/integrations/aionify-jira.user.js
// @updateURL    https://raw.githubusercontent.com/orange-buffalo/aionify/main/supplements/integrations/aionify-jira.user.js
// @grant        GM_xmlhttpRequest
// @grant        GM_registerMenuCommand
// ==/UserScript==

(function() {
    'use strict';

    // ========================================
    // CONFIGURATION - UPDATE THESE VALUES
    // ========================================
    const AIONIFY_BASE_URL = 'https://your-aionify-instance.com';  // Your Aionify URL (no trailing slash)
    const AIONIFY_API_TOKEN = 'your-aionify-api-token-here';       // Your Aionify API token from Settings
    const POLL_INTERVAL = 10000;  // Poll interval in milliseconds (10 seconds)
    // ========================================

    if (typeof console.debug === 'undefined') {
        console.debug = console.log;
    }

    console.debug('[Aionify Jira] Script loaded');

    // Parse Jira URL to extract issue key
    function parseJiraUrl() {
        // Match Jira issue keys (e.g., PROJ-123, proj-123, MULTI_KEY-123)
        // Jira keys start with a letter, can contain letters, numbers, and underscores, followed by hyphen and number
        const match = window.location.pathname.match(/\/browse\/([A-Z][A-Z0-9_]*-\d+)/i);
        if (!match) return null;
        
        return {
            issueKey: match[1]
        };
    }

    // Parse title from Jira page DOM
    function parseTitleFromPage() {
        // Try multiple selectors for different Jira UI versions
        const titleElement = document.querySelector('[data-testid="issue.views.issue-base.foundation.summary.heading"]') ||
                           document.querySelector('#summary-val') ||
                           document.querySelector('[data-test-id="issue.views.field.rich-text.heading"]');
        
        if (titleElement) {
            return titleElement.textContent.trim();
        }
        
        return null;
    }

    // Configuration for the time tracking button
    const config = {
        pollInterval: POLL_INTERVAL,
        
        getMetadataForCurrentPage: function() {
            const info = parseJiraUrl();
            if (!info) return [];
            
            return [`jiraIssue=${info.issueKey}`];
        },
        
        getTitleForCurrentPage: async function() {
            const info = parseJiraUrl();
            if (!info) {
                throw new Error('Failed to parse Jira URL');
            }
            
            // Get title from the page DOM
            const pageTitle = parseTitleFromPage();
            
            if (pageTitle) {
                return `${info.issueKey} ${pageTitle}`;
            }
            
            // Fallback: just use the issue key
            console.debug('[Aionify Jira] Could not find title on page, using issue key only');
            return info.issueKey;
        }
    };

    // Initialize menu
    function initialize() {
        const info = parseJiraUrl();
        if (!info) {
            console.debug('[Aionify Jira] Not on an issue page');
            return;
        }
        
        console.debug(`[Aionify Jira] Detected issue: ${info.issueKey}`);
        
        // Initialize the time tracking menu
        const client = new window.Aionify.Client(AIONIFY_BASE_URL, AIONIFY_API_TOKEN);
        const menu = new window.Aionify.Menu(client, config);
        
        menu.initialize();
    }

    // Wait for the Aionify engine to load
    function waitForEngine() {
        if (window.Aionify) {
            initialize();
        } else {
            setTimeout(waitForEngine, 100);
        }
    }

    waitForEngine();
})();
