// ==UserScript==
// @name         Aionify GitHub Integration
// @namespace    io.orangebuffalo.aionify
// @version      1.0.0
// @description  Start/stop Aionify time tracking from GitHub issues and pull requests
// @author       Aionify Contributors
// @license      Apache-2.0
// @match        https://github.com/*/*/issues/*
// @match        https://github.com/*/*/pull/*
// @require      https://raw.githubusercontent.com/orange-buffalo/aionify/main/supplements/integrations/aionify-engine.user.js
// @downloadURL  https://raw.githubusercontent.com/orange-buffalo/aionify/main/supplements/integrations/aionify-github.user.js
// @updateURL    https://raw.githubusercontent.com/orange-buffalo/aionify/main/supplements/integrations/aionify-github.user.js
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

    console.debug('[Aionify GitHub] Script loaded');

    // Parse GitHub URL to extract owner, repo, and number
    function parseGitHubUrl() {
        const match = window.location.pathname.match(/^\/([^/]+)\/([^/]+)\/(issues|pull)\/(\d+)/);
        if (!match) return null;
        
        return {
            owner: match[1],
            repo: match[2],
            type: match[3], // 'issues' or 'pull'
            number: match[4]
        };
    }

    // Parse title from GitHub page DOM
    function parseTitleFromPage() {
        // Try multiple selectors for different GitHub UI versions
        const titleElement = document.querySelector('.js-issue-title') ||
                           document.querySelector('[data-testid="issue-title"]') ||
                           document.querySelector('bdi.js-issue-title');
        
        if (titleElement) {
            return titleElement.textContent.trim();
        }
        
        return null;
    }

    // Configuration for the time tracking button
    const config = {
        pollInterval: POLL_INTERVAL,
        
        getMetadataForCurrentPage: function() {
            const info = parseGitHubUrl();
            if (!info) return [];
            
            const identifier = `${info.owner}/${info.repo}/${info.number}`;
            const metadataKey = info.type === 'issues' ? 'gitHubIssue' : 'gitHubPR';
            
            return [`${metadataKey}=${identifier}`];
        },
        
        getTitleForCurrentPage: async function() {
            const info = parseGitHubUrl();
            if (!info) {
                throw new Error('Failed to parse GitHub URL');
            }
            
            // Get title from the page DOM
            const pageTitle = parseTitleFromPage();
            const identifier = `${info.owner}/${info.repo}/${info.number}`;
            
            if (pageTitle) {
                return `${identifier} ${pageTitle}`;
            }
            
            // Fallback: just use the identifier
            console.debug('[Aionify GitHub] Could not find title on page, using identifier only');
            return identifier;
        }
    };

    // Initialize menu
    function initialize() {
        const info = parseGitHubUrl();
        if (!info) {
            console.debug('[Aionify GitHub] Not on an issue or PR page');
            return;
        }
        
        console.debug(`[Aionify GitHub] Detected ${info.type === 'issues' ? 'issue' : 'PR'}: ${info.owner}/${info.repo}/${info.number}`);
        
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
