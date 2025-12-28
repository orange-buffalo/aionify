import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./styles.css";
import "./lib/i18n";

const container = document.getElementById("root");
if (container) {
  createRoot(container).render(<App />);
}
