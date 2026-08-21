import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";

import App from "./App";
import "./index.css";
import { loadRuntimeConfig } from "@/config/runtime-config";

async function start() {
  await loadRuntimeConfig();

  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </StrictMode>,
  );
}

start().catch((error: unknown) => {
  const root = document.getElementById("root");

  if (root) {
    root.textContent =
      error instanceof Error ? error.message : "Frontend startup failed";
  }
});
