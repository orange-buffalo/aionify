import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./styles.css";
import "./lib/i18n";
import { getHostBridge } from "./lib/host-bridge";

// Detect an OS-specific wrapper hosting the app before anything else runs (see docs/os-wrappers.md)
getHostBridge();

const container = document.getElementById("root");
if (container) {
  createRoot(container).render(<App />);
}
