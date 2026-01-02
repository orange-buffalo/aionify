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
// ==/UserScript==

(function() {
    'use strict';

    // ========================================
    // CONFIGURATION - UPDATE THESE VALUES
    // ========================================
    const AIONIFY_BASE_URL = 'https://your-aionify-instance.com';  // Your Aionify URL (no trailing slash)
    const AIONIFY_API_TOKEN = 'your-aionify-api-token-here';       // Your Aionify API token from Settings
    const JIRA_BASE_URL = 'https://your-company.atlassian.net';    // Your Jira instance URL (no trailing slash)
    const JIRA_EMAIL = 'your-email@example.com';                    // Your Jira email
    const JIRA_API_TOKEN = 'your-jira-api-token-here';              // Your Jira API token
    const POLL_INTERVAL = 10000;  // Poll interval in milliseconds (10 seconds)
    // ========================================

    console.log('[Aionify Jira] Script loaded');

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

    // Fetch issue title from Jira API
    async function fetchTitle(issueKey) {
        return new Promise((resolve, reject) => {
            const url = `${JIRA_BASE_URL}/rest/api/3/issue/${issueKey}?fields=summary`;
            
            console.log(`[Aionify Jira] Fetching title from ${url}`);
            
            // Create Basic Auth header
            const auth = btoa(`${JIRA_EMAIL}:${JIRA_API_TOKEN}`);
            
            GM_xmlhttpRequest({
                method: 'GET',
                url: url,
                headers: {
                    'Authorization': `Basic ${auth}`,
                    'Accept': 'application/json',
                    'Content-Type': 'application/json'
                },
                onload: function(response) {
                    if (response.status === 200) {
                        try {
                            const data = JSON.parse(response.responseText);
                            resolve(data.fields.summary);
                        } catch (e) {
                            reject(new Error('Failed to parse Jira API response'));
                        }
                    } else {
                        reject(new Error(`Jira API returned ${response.status}`));
                    }
                },
                onerror: function() {
                    reject(new Error('Jira API request failed'));
                }
            });
        });
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
            
            try {
                // Try to fetch title from Jira API
                const title = await fetchTitle(info.issueKey);
                return `${info.issueKey} ${title}`;
            } catch (error) {
                console.error('[Aionify Jira] Failed to fetch title from API:', error);
                
                // Fallback: try to get title from the page
                const titleElement = document.querySelector('[data-testid="issue.views.issue-base.foundation.summary.heading"]');
                if (titleElement) {
                    const pageTitle = titleElement.textContent.trim();
                    return `${info.issueKey} ${pageTitle}`;
                }
                
                // Alternative selector for classic Jira
                const classicTitleElement = document.querySelector('#summary-val');
                if (classicTitleElement) {
                    const pageTitle = classicTitleElement.textContent.trim();
                    return `${info.issueKey} ${pageTitle}`;
                }
                
                // Last resort: just use the issue key
                return info.issueKey;
            }
        }
    };

    // Initialize when page is ready
    function initialize() {
        const info = parseJiraUrl();
        if (!info) {
            console.log('[Aionify Jira] Not on an issue page');
            return;
        }
        
        console.log(`[Aionify Jira] Detected issue: ${info.issueKey}`);
        
        // Wait for the page header to load
        const observer = new MutationObserver(() => {
            // Try to find the header actions area (new Jira UI)
            let actionsContainer = document.querySelector('[data-testid="issue.views.issue-base.foundation.quick-add.quick-add-item.container"]');
            
            if (!actionsContainer) {
                // Fallback for classic Jira UI
                actionsContainer = document.querySelector('.issue-header-actions');
            }
            
            if (!actionsContainer) {
                // Another fallback: find any suitable header element
                const issueHeader = document.querySelector('[data-testid="issue.views.issue-base.foundation.summary.heading"]');
                if (issueHeader) {
                    actionsContainer = issueHeader.parentElement;
                }
            }
            
            if (actionsContainer) {
                // Check if we've already inserted the button
                if (document.querySelector('#aionify-button-wrapper')) {
                    return;
                }
                
                observer.disconnect();
                
                // Create a wrapper for our button
                const buttonWrapper = document.createElement('div');
                buttonWrapper.id = 'aionify-button-wrapper';
                buttonWrapper.style.cssText = 'display: inline-block; margin-left: 8px; margin-right: 8px;';
                
                // Insert the wrapper into the actions container
                if (actionsContainer.tagName === 'H1') {
                    // If it's a heading, insert after it
                    actionsContainer.parentElement.insertBefore(buttonWrapper, actionsContainer.nextSibling);
                } else {
                    // Otherwise, insert at the beginning
                    actionsContainer.insertBefore(buttonWrapper, actionsContainer.firstChild);
                }
                
                // Initialize the time tracking button
                const client = new window.Aionify.Client(AIONIFY_BASE_URL, AIONIFY_API_TOKEN);
                const timeTracker = new window.Aionify.Button(client, config);
                
                timeTracker.initialize((buttonElement) => {
                    buttonWrapper.appendChild(buttonElement);
                });
            }
        });
        
        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
        
        // Timeout after 10 seconds if header not found
        setTimeout(() => observer.disconnect(), 10000);
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
