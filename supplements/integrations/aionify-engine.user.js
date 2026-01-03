// ==UserScript==
// @name         Aionify Time Tracking Engine
// @namespace    io.orangebuffalo.aionify
// @version      1.0.0
// @description  Reusable engine for Aionify browser integrations
// @author       Aionify Contributors
// @license      Apache-2.0
// @downloadURL  https://raw.githubusercontent.com/orange-buffalo/aionify/main/supplements/integrations/aionify-engine.user.js
// @updateURL    https://raw.githubusercontent.com/orange-buffalo/aionify/main/supplements/integrations/aionify-engine.user.js
// @grant        GM_xmlhttpRequest
// @grant        GM_registerMenuCommand
// ==/UserScript==

(function() {
    'use strict';

    // Polyfill console.debug if not available
    if (typeof console.debug === 'undefined') {
        console.debug = console.log;
    }

    // Aionify API client
    class AionifyClient {
        constructor(baseUrl, apiToken) {
            this.baseUrl = baseUrl;
            this.apiToken = apiToken;
        }

        async request(endpoint, method = 'GET', body = null) {
            return new Promise((resolve, reject) => {
                const url = `${this.baseUrl}${endpoint}`;
                const headers = {
                    'Authorization': `Bearer ${this.apiToken}`,
                    'Content-Type': 'application/json'
                };

                console.debug(`[Aionify] ${method} ${url}`);

                GM_xmlhttpRequest({
                    method: method,
                    url: url,
                    headers: headers,
                    data: body ? JSON.stringify(body) : null,
                    onload: function(response) {
                        console.debug(`[Aionify] Response ${response.status}: ${response.responseText.substring(0, 200)}`);
                        if (response.status >= 200 && response.status < 300) {
                            try {
                                const data = response.responseText ? JSON.parse(response.responseText) : {};
                                resolve(data);
                            } catch (e) {
                                resolve({});
                            }
                        } else if (response.status === 404) {
                            resolve(null);
                        } else {
                            reject(new Error(`HTTP ${response.status}: ${response.statusText}`));
                        }
                    },
                    onerror: function(response) {
                        console.error('[Aionify] Request failed:', response);
                        reject(new Error('Network request failed'));
                    }
                });
            });
        }

        async getActiveEntry() {
            const response = await this.request('/api/time-log-entries/active');
            return response; // null if 404, object if active entry exists
        }

        async startEntry(title, metadata = []) {
            return await this.request('/api/time-log-entries/start', 'POST', {
                title: title,
                metadata: metadata
            });
        }

        async stopEntry() {
            return await this.request('/api/time-log-entries/stop', 'POST');
        }
    }

    // Time tracking menu manager
    class TimeTrackingMenu {
        constructor(aionifyClient, config) {
            this.aionifyClient = aionifyClient;
            this.config = config;
            this.menuCommandId = null;
            this.pollInterval = null;
            this.isProcessing = false;
            this.isActiveEntry = false;
        }

        updateMenuCommand() {
            // Remove old menu command if it exists
            if (this.menuCommandId !== null && typeof GM_unregisterMenuCommand !== 'undefined') {
                GM_unregisterMenuCommand(this.menuCommandId);
            }
            
            // Register new menu command with current state
            const menuText = this.isActiveEntry ? '⏱️ Stop Time Tracking' : '⏱️ Start Time Tracking';
            const handler = this.isActiveEntry ? () => this.handleStop() : () => this.handleStart();
            
            this.menuCommandId = GM_registerMenuCommand(menuText, handler);
        }

        async handleStart() {
            if (this.isProcessing) return;
            
            this.isProcessing = true;
            
            try {
                console.debug('[Aionify] Starting time entry...');
                const title = await this.config.getTitleForCurrentPage();
                const metadata = this.config.getMetadataForCurrentPage();
                
                await this.aionifyClient.startEntry(title, metadata);
                console.debug('[Aionify] Time entry started successfully');
                
                // Force immediate poll to update menu state
                await this.poll();
            } catch (error) {
                console.error('[Aionify] Failed to start time entry:', error);
                alert('Failed to start time entry. Please check the console for details.');
            } finally {
                this.isProcessing = false;
            }
        }

        async handleStop() {
            if (this.isProcessing) return;
            
            this.isProcessing = true;
            
            try {
                console.debug('[Aionify] Stopping time entry...');
                await this.aionifyClient.stopEntry();
                console.debug('[Aionify] Time entry stopped successfully');
                
                // Force immediate poll to update menu state
                await this.poll();
            } catch (error) {
                console.error('[Aionify] Failed to stop time entry:', error);
                alert('Failed to stop time entry. Please check the console for details.');
            } finally {
                this.isProcessing = false;
            }
        }

        async poll() {
            try {
                const activeEntry = await this.aionifyClient.getActiveEntry();
                const currentMetadata = this.config.getMetadataForCurrentPage();
                
                let isMatchingEntry = false;
                
                if (activeEntry && activeEntry.metadata) {
                    // Check if any of the current page's metadata matches the active entry's metadata
                    isMatchingEntry = currentMetadata.some(meta => 
                        activeEntry.metadata.includes(meta)
                    );
                }
                
                // Update menu if state changed
                if (this.isActiveEntry !== isMatchingEntry) {
                    this.isActiveEntry = isMatchingEntry;
                    this.updateMenuCommand();
                }
            } catch (error) {
                console.error('[Aionify] Polling failed:', error);
            }
        }

        async initialize() {
            // Initial poll
            await this.poll();
            
            // Start polling interval
            this.pollInterval = setInterval(() => this.poll(), this.config.pollInterval || 10000);
            
            console.debug('[Aionify] Time tracking menu initialized');
        }

        destroy() {
            if (this.pollInterval) {
                clearInterval(this.pollInterval);
                this.pollInterval = null;
            }
            if (this.menuCommandId !== null && typeof GM_unregisterMenuCommand !== 'undefined') {
                GM_unregisterMenuCommand(this.menuCommandId);
                this.menuCommandId = null;
            }
        }
    }

    // Export for use in integration scripts
    window.Aionify = {
        Client: AionifyClient,
        Menu: TimeTrackingMenu
    };

    console.debug('[Aionify] Engine loaded');
})();
