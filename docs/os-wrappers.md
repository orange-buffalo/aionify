# OS Wrappers Guide

This guide is for developers building OS-specific wrappers for Aionify, such as a menu bar or tray app, a desktop shell or a mobile app, that host the Aionify web app in a webview.

## Architecture

A wrapper uses two integration points, each for what it does best:

1. **The [Public API](./public-api.md)** is the only contract for data, actions and events. Use it to read the active entry, start and stop tracking, and subscribe to the event stream. It works whether or not the webview is open, e.g. from a tray icon while the window is closed.
2. **The host bridge** is a small message channel between the wrapper and the web app loaded in its webview. It covers only what the page knows or can do: reporting login state, navigating to app pages, and provisioning an API token after the user agrees.

The bridge does not proxy API calls and never exposes the web session (JWT or remember-me cookie) to the wrapper.

## Recommended Flow

1. Load your Aionify instance URL in the webview and inject the bridge bootstrap script (see [Platform Setup](#platform-setup)).
2. Answer the app's `host.hello` request.
3. Wait for an `auth.changed` event with `authenticated: true`.
4. If the wrapper has no stored API token, send `auth.provisionApiToken` with a descriptive name, e.g. `Aionify for macOS (work laptop)`. The user sees a consent dialog in the app.
5. Store the returned token in the OS credential store (Keychain, Credential Manager, Android Keystore, etc.).
6. Use the token with the public API:
   - `GET /api/time-log-entries/active` for the current state
   - `POST /api/time-log-entries/start` and `POST /api/time-log-entries/stop` for actions
   - `GET /api/time-log-entries/events` for real-time updates, e.g. to update the tray title or show native notifications
7. If the public API responds with `401`, the user has revoked the token in Settings. Delete it and provision a new one the next time the user is logged in.

Users can see and revoke tokens created by wrappers under **Settings → API Access Tokens**.

## Bridge Contract

### Transport

The wrapper and the app exchange **JSON-encoded strings**:

| Direction | Mechanism |
|---|---|
| App → wrapper | The wrapper defines `window.aionifyHost.postMessage(message: string)` **before the app loads**, forwarding the message to native code. |
| Wrapper → app | The wrapper calls `window.aionify.receive(message: string)` in the page. |

The app detects `window.aionifyHost` once at startup. Without it, e.g. in a regular browser, the bridge is disabled and `window.aionify` is not defined. A host object added after startup is ignored.

### Messages

```jsonc
// Request (either direction). Ids must be unique per sender.
{ "type": "request", "id": "host-42", "method": "app.navigate", "params": { "path": "/portal/settings" } }

// Successful response
{ "type": "response", "id": "host-42", "result": {} }

// Error response
{ "type": "response", "id": "host-42", "error": { "code": "INVALID_PARAMS", "message": "..." } }

// Event (no response expected)
{ "type": "event", "event": "auth.changed", "payload": { "authenticated": true, "userName": "fry" } }
```

Messages that can't be parsed or don't match this shape are ignored.

### Lifecycle

- After loading, the app sends a `host.hello` request. The wrapper **must** respond to it; until it does, the app sends no events.
- Every full page (re)load starts a new app instance with a new handshake. Treat a new `host.hello` as a reset: fail any requests still pending from the previous instance and expect a fresh `auth.changed` event.
- In-app navigation does not reload the page and keeps the bridge connected.

### Wrapper Methods (app → wrapper)

#### `host.hello`

Sent by the app once after it loads.

Params:
```json
{
  "protocolVersion": 1,
  "methods": ["app.navigate", "auth.provisionApiToken"],
  "events": ["auth.changed"]
}
```

The wrapper must respond with its own protocol version. Any other fields are optional and ignored by the app:
```json
{ "protocolVersion": 1, "platform": "macos", "wrapperVersion": "1.2.0" }
```

If the response is an error or has no valid `protocolVersion` (an integer ≥ 1), the handshake fails and the app does not send events.

### App Methods (wrapper → app)

#### `app.navigate`

Opens a page of the app, e.g. when the user clicks a native notification.

| | |
|---|---|
| Params | `{ "path": string }`: an in-app path starting with a single `/`, e.g. `/portal/time-logs` |
| Result | `{}` |
| Errors | `INVALID_PARAMS` if the path is missing or could lead outside of the app |

Pages the user can't access redirect as usual, e.g. to the login page.

#### `auth.provisionApiToken`

Asks the user to allow the wrapper to access their account. If the user agrees, a dedicated API token is created.

| | |
|---|---|
| Params | `{ "name": string }`: token name shown to the user, 1-100 characters after trimming, unique among the user's tokens |
| Result | `{ "name": string, "token": string }` |
| Errors | `INVALID_PARAMS`, `NOT_AUTHENTICATED` (user not logged in), `REQUEST_IN_PROGRESS` (another consent dialog is open), `USER_REJECTED`, `API_TOKEN_NAME_ALREADY_EXISTS`, `API_TOKEN_LIMIT_REACHED`, `TOKEN_PROVISIONING_FAILED` |

The response arrives only after the user decides, which can take any amount of time. The wrapper is responsible for showing errors, e.g. asking the user to pick another name.

### Events (app → wrapper)

#### `auth.changed`

Sent after the handshake with the current state, and every time the login state changes (login, logout, a different user logging in).

Payload: `{ "authenticated": boolean, "userName"?: string }`. `userName` is present only when authenticated.

### Generic Errors

| Code | Meaning |
|---|---|
| `UNKNOWN_METHOD` | The receiver does not support the method |
| `INTERNAL_ERROR` | Unexpected failure while handling the request |

Wrappers should also respond with `UNKNOWN_METHOD` to app requests they don't support.

### Compatibility Rules

- Within a protocol version, changes are additive: new methods, events and fields may be added.
- Both sides must ignore fields and events they don't know.
- Breaking changes require a new `protocolVersion`.

## Platform Setup

The snippets below show only the bridge wiring. In every case:
- inject the bootstrap only into the main frame, and
- only accept messages that come from your Aionify instance's origin.

### Windows (WebView2)

```csharp
await webView.CoreWebView2.AddScriptToExecuteOnDocumentCreatedAsync(
    "window.aionifyHost = { postMessage: (message) => window.chrome.webview.postMessage(message) };");

webView.CoreWebView2.WebMessageReceived += (sender, args) =>
{
    if (!args.Source.StartsWith(AionifyOrigin)) return;
    var message = args.TryGetWebMessageAsString();
    // Parse the JSON message and handle it
};

Task SendToApp(string message) =>
    webView.CoreWebView2.ExecuteScriptAsync($"window.aionify.receive({JsonSerializer.Serialize(message)})");
```

### macOS / iOS (WKWebView)

```swift
let bootstrap = WKUserScript(
    source: "window.aionifyHost = { postMessage: (message) => window.webkit.messageHandlers.aionify.postMessage(message) };",
    injectionTime: .atDocumentStart,
    forMainFrameOnly: true
)
configuration.userContentController.addUserScript(bootstrap)
configuration.userContentController.add(bridgeHandler, name: "aionify")

// In bridgeHandler (WKScriptMessageHandler):
func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
    guard message.frameInfo.isMainFrame,
          message.frameInfo.securityOrigin.host == aionifyHost,
          let json = message.body as? String else { return }
    // Parse the JSON message and handle it
}

func sendToApp(_ message: String) {
    webView.callAsyncJavaScript("window.aionify.receive(message)", arguments: ["message": message], in: nil, in: .page)
}
```

### Android (WebView)

```kotlin
val allowedOrigins = setOf(AIONIFY_ORIGIN)

WebViewCompat.addWebMessageListener(webView, "AionifyNative", allowedOrigins) { _, message, _, isMainFrame, _ ->
    if (isMainFrame) {
        val json = message.data ?: return@addWebMessageListener
        // Parse the JSON message and handle it
    }
}

WebViewCompat.addDocumentStartJavaScript(
    webView,
    "window.aionifyHost = { postMessage: (message) => AionifyNative.postMessage(message) };",
    allowedOrigins,
)

fun sendToApp(message: String) {
    webView.evaluateJavascript("window.aionify.receive(${JSONObject.quote(message)})", null)
}
```

Both APIs require checking `WebViewFeature.WEB_MESSAGE_LISTENER` and `WebViewFeature.DOCUMENT_START_SCRIPT` support.

### Electron

```js
// preload.js (with contextIsolation enabled)
const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld("aionifyHost", {
  postMessage: (message) => ipcRenderer.send("aionify:message", String(message)),
});

// main process
ipcMain.on("aionify:message", (event, message) => {
  if (!event.senderFrame.url.startsWith(AIONIFY_ORIGIN)) return;
  // Parse the JSON message and handle it
});

function sendToApp(message) {
  return window.webContents.executeJavaScript(`window.aionify.receive(${JSON.stringify(message)})`);
}
```

## Security Considerations

- The wrapper gets a revocable API token, never the user's web session. Store it only in the OS credential store.
- Treat all messages from the app as untrusted input: validate them and never execute anything derived from them.
- Aionify refuses to render inside frames on other sites, and the bridge only activates when the host object is present at startup. Don't load untrusted content into the same webview.
- Use HTTPS for your Aionify instance.
