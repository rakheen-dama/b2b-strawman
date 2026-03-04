import { createRoot } from "react-dom/client";
import { StrictMode } from "react";
import { KcPage } from "./login/KcPage";
import type { KcContext } from "./login/KcContext";
import "./main.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    {window.kcContext ? (
      <KcPage kcContext={window.kcContext} />
    ) : (
      <div style={{ padding: "2rem" }}>
        <h1>Keycloakify Theme Dev Mode</h1>
        <p>This page only renders inside Keycloak. Start Keycloak to see the theme.</p>
      </div>
    )}
  </StrictMode>
);

declare global {
  interface Window {
    kcContext?: KcContext;
  }
}
