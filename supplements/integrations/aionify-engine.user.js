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
// @grant        GM_addStyle
// ==/UserScript==

(function() {
    'use strict';

    // Aionify favicon SVG (base64 encoded)
    const AIONIFY_ICON = 'data:image/svg+xml;base64,PHN2ZyB2ZXJzaW9uPSIxLjEiIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDMyIDMyIiBmaWxsPSJub25lIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPgogIDwhLS0gT3V0ZXIgYmx1ZSBjaXJjbGUgLS0+CiAgPGNpcmNsZSBjeD0iMTYiIGN5PSIxNiIgcj0iMTYiIGZpbGw9IiM3NmFhZDUiLz4KICA8IS0tIElubmVyIGNsb2NrIGZhY2UgKGRhcmsgYWNjZW50KSAtLT4KICA8Y2lyY2xlIGN4PSIxNiIgY3k9IjE2IiByPSIxNCIgZmlsbD0iIzM1MzY0OCIvPgogIDwhLS0gQ2xvY2sgaGFuZHMgLS0+CiAgPGxpbmUgeDE9IjE2IiB5MT0iMTYiIHgyPSIxMiIgeTI9IjEwIiBzdHJva2U9IiMzN2EyZWEiIHN0cm9rZS13aWR0aD0iMi4yIiBzdHJva2UtbGluZWNhcD0icm91bmQiLz4KICA8bGluZSB4MT0iMTYiIHkxPSIxNiIgeDI9IjIyIiB5Mj0iMTAiIHN0cm9rZT0iIzQ3OTdkOSIgc3Ryb2tlLXdpZHRoPSIyLjIiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIvPgogIDwhLS0gQ2VudGVyIGRvdCAtLT4KICA8Y2lyY2xlIGN4PSIxNiIgY3k9IjE2IiByPSIxLjciIGZpbGw9IiNGRkYiIC8+Cjwvc3ZnPg==';

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

                console.log(`[Aionify] ${method} ${url}`);

                GM_xmlhttpRequest({
                    method: method,
                    url: url,
                    headers: headers,
                    data: body ? JSON.stringify(body) : null,
                    onload: function(response) {
                        console.log(`[Aionify] Response ${response.status}: ${response.responseText.substring(0, 200)}`);
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

    // Time tracking button manager
    class TimeTrackingButton {
        constructor(aionifyClient, config) {
            this.aionifyClient = aionifyClient;
            this.config = config;
            this.button = null;
            this.pollInterval = null;
            this.isProcessing = false;
        }

        createButton() {
            const button = document.createElement('button');
            button.style.cssText = `
                display: inline-flex;
                align-items: center;
                gap: 8px;
                padding: 6px 12px;
                background-color: #76aad5;
                color: white;
                border: none;
                border-radius: 6px;
                font-size: 14px;
                font-weight: 600;
                cursor: pointer;
                transition: background-color 0.2s;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
            `;
            button.onmouseover = () => button.style.backgroundColor = '#5a8fb8';
            button.onmouseout = () => button.style.backgroundColor = '#76aad5';
            
            const icon = document.createElement('img');
            icon.src = AIONIFY_ICON;
            icon.style.cssText = 'width: 16px; height: 16px;';
            button.appendChild(icon);
            
            const text = document.createElement('span');
            button.appendChild(text);
            
            return button;
        }

        updateButton(isActive) {
            if (!this.button) return;
            
            const text = this.button.querySelector('span');
            if (isActive) {
                text.textContent = 'Stop';
                this.button.onclick = () => this.handleStop();
            } else {
                text.textContent = 'Start';
                this.button.onclick = () => this.handleStart();
            }
            
            this.button.disabled = this.isProcessing;
            this.button.style.opacity = this.isProcessing ? '0.6' : '1';
            this.button.style.cursor = this.isProcessing ? 'not-allowed' : 'pointer';
        }

        async handleStart() {
            if (this.isProcessing) return;
            
            this.isProcessing = true;
            this.updateButton(false);
            
            try {
                console.log('[Aionify] Starting time entry...');
                const title = await this.config.getTitleForCurrentPage();
                const metadata = this.config.getMetadataForCurrentPage();
                
                await this.aionifyClient.startEntry(title, metadata);
                console.log('[Aionify] Time entry started successfully');
                
                // Force immediate poll to update button state
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
            this.updateButton(true);
            
            try {
                console.log('[Aionify] Stopping time entry...');
                await this.aionifyClient.stopEntry();
                console.log('[Aionify] Time entry stopped successfully');
                
                // Force immediate poll to update button state
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
                
                this.updateButton(isMatchingEntry);
            } catch (error) {
                console.error('[Aionify] Polling failed:', error);
            }
        }

        async initialize(insertButton) {
            // Create and insert button
            this.button = this.createButton();
            insertButton(this.button);
            
            // Initial poll
            await this.poll();
            
            // Start polling interval
            this.pollInterval = setInterval(() => this.poll(), this.config.pollInterval || 10000);
            
            console.log('[Aionify] Time tracking button initialized');
        }

        destroy() {
            if (this.pollInterval) {
                clearInterval(this.pollInterval);
                this.pollInterval = null;
            }
            if (this.button) {
                this.button.remove();
                this.button = null;
            }
        }
    }

    // Export for use in integration scripts
    window.Aionify = {
        Client: AionifyClient,
        Button: TimeTrackingButton,
        ICON: AIONIFY_ICON
    };

    console.log('[Aionify] Engine loaded');
})();
