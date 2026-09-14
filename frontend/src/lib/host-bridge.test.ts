import { describe, expect, test } from "bun:test";
import { HOST_BRIDGE_PROTOCOL_VERSION, HostBridge, HostBridgeError, isInAppPath } from "./host-bridge";

class FakeHost {
  readonly messages: any[] = [];

  postMessage(message: string) {
    this.messages.push(JSON.parse(message));
  }

  lastMessage() {
    return this.messages[this.messages.length - 1];
  }
}

function flushAsync() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

async function startedBridge(host: FakeHost) {
  const bridge = new HostBridge(host, ["auth.changed"]);
  const started = bridge.start();
  const hello = host.lastMessage();
  bridge.receive(JSON.stringify({ type: "response", id: hello.id, result: { protocolVersion: 1 } }));
  await started;
  return bridge;
}

describe("HostBridge handshake", () => {
  test("announces protocol version, registered methods and events", async () => {
    const host = new FakeHost();
    const bridge = new HostBridge(host, ["auth.changed"]);
    bridge.registerHandler("app.navigate", () => ({}));

    bridge.start();

    expect(host.messages).toEqual([
      {
        type: "request",
        id: "app-1",
        method: "host.hello",
        params: {
          protocolVersion: HOST_BRIDGE_PROTOCOL_VERSION,
          methods: ["app.navigate"],
          events: ["auth.changed"],
        },
      },
    ]);
  });

  test("delivers events only after a successful handshake", async () => {
    const host = new FakeHost();
    const bridge = new HostBridge(host, ["auth.changed"]);

    expect(bridge.emitEvent("auth.changed", { authenticated: false })).toBe(false);

    const started = bridge.start();
    bridge.receive({ type: "response", id: "app-1", result: { protocolVersion: 1 } });
    await started;

    expect(bridge.emitEvent("auth.changed", { authenticated: false })).toBe(true);
    expect(host.lastMessage()).toEqual({ type: "event", event: "auth.changed", payload: { authenticated: false } });
  });

  test("returns the same handshake for repeated start calls", () => {
    const host = new FakeHost();
    const bridge = new HostBridge(host, []);

    expect(bridge.start()).toBe(bridge.start());
    expect(host.messages.length).toBe(1);
  });

  test("fails when host responds with unsupported protocol version", async () => {
    const host = new FakeHost();
    const bridge = new HostBridge(host, []);

    const started = bridge.start();
    bridge.receive(JSON.stringify({ type: "response", id: "app-1", result: { protocolVersion: 0 } }));

    await expect(started).rejects.toMatchObject({ code: "UNSUPPORTED_PROTOCOL_VERSION" });
    expect(bridge.emitEvent("auth.changed")).toBe(false);
  });

  test("fails when host responds with a newer protocol version", async () => {
    const host = new FakeHost();
    const bridge = new HostBridge(host, []);

    const started = bridge.start();
    bridge.receive(JSON.stringify({ type: "response", id: "app-1", result: { protocolVersion: 2 } }));

    await expect(started).rejects.toMatchObject({ code: "UNSUPPORTED_PROTOCOL_VERSION" });
    expect(bridge.emitEvent("auth.changed")).toBe(false);
  });

  test("fails when host responds with an error", async () => {
    const host = new FakeHost();
    const bridge = new HostBridge(host, []);

    const started = bridge.start();
    bridge.receive(JSON.stringify({ type: "response", id: "app-1", error: { code: "NOPE", message: "Not today" } }));

    await expect(started).rejects.toMatchObject({ code: "NOPE", message: "Not today" });
  });
});

describe("HostBridge requests from host", () => {
  test("responds with handler result", async () => {
    const host = new FakeHost();
    const bridge = await startedBridge(host);
    bridge.registerHandler("app.navigate", (params) => ({ received: params }));

    bridge.receive(JSON.stringify({ type: "request", id: "host-1", method: "app.navigate", params: { path: "/x" } }));
    await flushAsync();

    expect(host.lastMessage()).toEqual({ type: "response", id: "host-1", result: { received: { path: "/x" } } });
  });

  test("responds with null result when handler returns nothing", async () => {
    const host = new FakeHost();
    const bridge = await startedBridge(host);
    bridge.registerHandler("noop", () => undefined);

    bridge.receive({ type: "request", id: "host-1", method: "noop" });
    await flushAsync();

    expect(host.lastMessage()).toEqual({ type: "response", id: "host-1", result: null });
  });

  test("responds with error for unknown method", async () => {
    const host = new FakeHost();
    const bridge = await startedBridge(host);

    bridge.receive(JSON.stringify({ type: "request", id: "host-1", method: "fs.readFile" }));
    await flushAsync();

    expect(host.lastMessage()).toEqual({
      type: "response",
      id: "host-1",
      error: { code: "UNKNOWN_METHOD", message: "Unknown method: fs.readFile" },
    });
  });

  test("responds with unregistered method as unknown", async () => {
    const host = new FakeHost();
    const bridge = await startedBridge(host);
    const unregister = bridge.registerHandler("app.navigate", () => ({}));
    unregister();

    bridge.receive({ type: "request", id: "host-1", method: "app.navigate" });
    await flushAsync();

    expect(host.lastMessage().error.code).toBe("UNKNOWN_METHOD");
  });

  test("responds with bridge error code thrown by handler", async () => {
    const host = new FakeHost();
    const bridge = await startedBridge(host);
    bridge.registerHandler("app.navigate", async () => {
      throw new HostBridgeError("INVALID_PARAMS", "Bad path");
    });

    bridge.receive({ type: "request", id: "host-1", method: "app.navigate" });
    await flushAsync();

    expect(host.lastMessage()).toEqual({
      type: "response",
      id: "host-1",
      error: { code: "INVALID_PARAMS", message: "Bad path" },
    });
  });

  test("responds with internal error for unexpected handler failures", async () => {
    const host = new FakeHost();
    const bridge = await startedBridge(host);
    bridge.registerHandler("app.navigate", () => {
      throw new Error("Boom");
    });

    bridge.receive({ type: "request", id: "host-1", method: "app.navigate" });
    await flushAsync();

    expect(host.lastMessage()).toEqual({
      type: "response",
      id: "host-1",
      error: { code: "INTERNAL_ERROR", message: "Boom" },
    });
  });

  test("ignores malformed messages and responses to unknown requests", async () => {
    const host = new FakeHost();
    const bridge = await startedBridge(host);
    const messagesBefore = host.messages.length;

    bridge.receive("not json");
    bridge.receive(JSON.stringify(["request"]));
    bridge.receive(JSON.stringify({ type: "request", method: "app.navigate" }));
    bridge.receive(JSON.stringify({ type: "response", id: "unknown", result: {} }));
    bridge.receive(JSON.stringify({ type: "response", id: "app-1", error: "oops" }));
    bridge.receive(JSON.stringify({ type: "event", event: "host.something" }));
    bridge.receive(null);
    await flushAsync();

    expect(host.messages.length).toBe(messagesBefore);
  });
});

describe("isInAppPath", () => {
  test("accepts app paths", () => {
    expect(isInAppPath("/")).toBe(true);
    expect(isInAppPath("/portal/time-logs")).toBe(true);
    expect(isInAppPath("/portal/settings?tab=api#tokens")).toBe(true);
  });

  test("rejects paths leading outside of the app", () => {
    expect(isInAppPath("")).toBe(false);
    expect(isInAppPath("portal/time-logs")).toBe(false);
    expect(isInAppPath("https://example.com")).toBe(false);
    expect(isInAppPath("//example.com")).toBe(false);
    expect(isInAppPath("/\\example.com")).toBe(false);
    expect(isInAppPath("javascript:alert(1)")).toBe(false);
    expect(isInAppPath("/portal\n/x")).toBe(false);
  });
});
