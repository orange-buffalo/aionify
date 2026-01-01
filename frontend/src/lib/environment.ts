/**
 * Utility functions for detecting the runtime environment.
 */

/**
 * Checks if the code is running in an automated test environment (e.g., Playwright, Selenium).
 * This is useful for disabling features like SSE connections during automated testing.
 *
 * @returns true if running in a test/automated environment, false otherwise
 */
export function isTestEnvironment(): boolean {
  return typeof navigator !== "undefined" && navigator.webdriver === true;
}

/**
 * Checks if the code is running in a browser environment (vs. server-side rendering).
 *
 * @returns true if running in a browser, false otherwise
 */
export function isBrowser(): boolean {
  return typeof window !== "undefined";
}
