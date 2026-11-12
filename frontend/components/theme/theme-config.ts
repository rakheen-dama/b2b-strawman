// Shared by the client `KaziThemeProvider` and the server root layout's blocking head script, so
// the storage key, the themed-route rule and the default can never drift apart (ADR-371). No
// "use client": the root layout (a Server Component) imports the script string from here.

/** localStorage key of the per-browser theme choice (ADR-371). */
export const THEME_STORAGE_KEY = "kazi-theme";

/** Theme painted by default and forced on every route outside the org-scoped app. */
export const DEFAULT_THEME = "light";

/** Only routes under this prefix are themed (ADR-371, B6). */
export const THEMED_ROUTE_PREFIX = "/org/";

/** Only the org-scoped app is themed; every other route always paints light (ADR-371, B6). */
export function isThemedRoute(pathname: string | null): boolean {
  return pathname?.startsWith(THEMED_ROUTE_PREFIX) ?? false;
}

/**
 * Parser-blocking script for the root layout's `<head>`. It runs before any `<body>` content can
 * paint and applies the same result as next-themes 0.4.6 with `attribute="class"`: one of
 * `light` / `dark` on `<html>` (the other removed) plus `style.colorScheme`. A stored `system`
 * follows `prefers-color-scheme`; a missing, unknown or unreadable value, and every route outside
 * `THEMED_ROUTE_PREFIX`, resolve to `DEFAULT_THEME`. next-themes' own script and provider still
 * run afterwards and own the theme at runtime; re-applying the same class is idempotent.
 */
export const THEME_INIT_SCRIPT = `(function(){try{var d=document.documentElement,t=${JSON.stringify(
  DEFAULT_THEME
)};if(location.pathname.indexOf(${JSON.stringify(
  THEMED_ROUTE_PREFIX
)})===0){var s=null;try{s=localStorage.getItem(${JSON.stringify(
  THEME_STORAGE_KEY
)})}catch(e){}if(s==="system"){s=window.matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light"}if(s==="dark"||s==="light"){t=s}}d.classList.remove(t==="dark"?"light":"dark");d.classList.add(t);d.style.colorScheme=t}catch(e){}})()`;
