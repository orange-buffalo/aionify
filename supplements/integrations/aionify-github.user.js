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
// @grant        GM_addStyle
// ==/UserScript==

(function() {
    'use strict';

    // ========================================
    // CONFIGURATION - UPDATE THESE VALUES
    // ========================================
    const AIONIFY_BASE_URL = 'https://your-aionify-instance.com';  // Your Aionify URL (no trailing slash)
    const AIONIFY_API_TOKEN = 'your-aionify-api-token-here';       // Your Aionify API token from Settings
    const GITHUB_TOKEN = 'your-github-token-here';                  // Your GitHub personal access token
    const POLL_INTERVAL = 10000;  // Poll interval in milliseconds (10 seconds)
    // ========================================

    console.log('[Aionify GitHub] Script loaded');

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

    // Fetch issue/PR title from GitHub API
    async function fetchTitle(owner, repo, type, number) {
        return new Promise((resolve, reject) => {
            const endpoint = type === 'issues' ? 'issues' : 'pulls';
            const url = `https://api.github.com/repos/${owner}/${repo}/${endpoint}/${number}`;
            
            console.log(`[Aionify GitHub] Fetching title from ${url}`);
            
            GM_xmlhttpRequest({
                method: 'GET',
                url: url,
                headers: {
                    'Authorization': `token ${GITHUB_TOKEN}`,
                    'Accept': 'application/vnd.github.v3+json',
                    'User-Agent': 'Aionify-GitHub-Integration'
                },
                onload: function(response) {
                    if (response.status === 200) {
                        try {
                            const data = JSON.parse(response.responseText);
                            resolve(data.title);
                        } catch (e) {
                            reject(new Error('Failed to parse GitHub API response'));
                        }
                    } else {
                        reject(new Error(`GitHub API returned ${response.status}`));
                    }
                },
                onerror: function() {
                    reject(new Error('GitHub API request failed'));
                }
            });
        });
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
            
            try {
                // Try to fetch title from GitHub API
                const title = await fetchTitle(info.owner, info.repo, info.type, info.number);
                const identifier = `${info.owner}/${info.repo}/${info.number}`;
                return `${identifier} ${title}`;
            } catch (error) {
                console.error('[Aionify GitHub] Failed to fetch title from API:', error);
                
                // Fallback: try to get title from the page
                const titleElement = document.querySelector('.js-issue-title, .gh-header-title .js-issue-title');
                if (titleElement) {
                    const pageTitle = titleElement.textContent.trim();
                    const identifier = `${info.owner}/${info.repo}/${info.number}`;
                    return `${identifier} ${pageTitle}`;
                }
                
                // Last resort: just use the identifier
                const identifier = `${info.owner}/${info.repo}/${info.number}`;
                return identifier;
            }
        }
    };

    // Initialize when page is ready
    function initialize() {
        const info = parseGitHubUrl();
        if (!info) {
            console.log('[Aionify GitHub] Not on an issue or PR page');
            return;
        }
        
        console.log(`[Aionify GitHub] Detected ${info.type === 'issues' ? 'issue' : 'PR'}: ${info.owner}/${info.repo}/${info.number}`);
        
        // Wait for the page header to load
        const observer = new MutationObserver(() => {
            // Try to find the header actions area
            let actionsContainer = document.querySelector('.gh-header-actions');
            
            if (!actionsContainer) {
                // Fallback: try alternative selectors
                actionsContainer = document.querySelector('.timeline-comment-actions');
            }
            
            if (actionsContainer) {
                observer.disconnect();
                
                // Create a wrapper for our button
                const buttonWrapper = document.createElement('div');
                buttonWrapper.style.cssText = 'display: inline-block; margin-left: 8px;';
                
                // Insert the wrapper into the actions container
                actionsContainer.insertBefore(buttonWrapper, actionsContainer.firstChild);
                
                // Initialize the time tracking button
                const client = new window.Aionify.Client(AIONIFY_BASE_URL, AIONIFY_API_TOKEN);
                const button = new window.Aionify.Button(client, config);
                
                button.initialize((buttonElement) => {
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
