import React from "react";
import { renderToString } from "react-dom/server";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

const { nav } = vi.hoisted(() => ({ nav: { pathname: "/org/acme/dashboard" as string | null } }));
vi.mock("next/navigation", () => ({ usePathname: () => nav.pathname }));

import {
  THEME_INIT_SCRIPT,
  THEME_STORAGE_KEY,
  isThemedRoute,
} from "@/components/theme/theme-config";
import { KaziThemeProvider } from "@/components/theme/theme-provider";

const html = document.documentElement;

function resetHtml() {
  html.className = "";
  html.style.colorScheme = "";
}

function stubOsDark(dark: boolean) {
  window.matchMedia = vi.fn((query: string) => ({
    matches: query === "(prefers-color-scheme: dark)" && dark,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })) as unknown as typeof window.matchMedia;
}

function setup(path: string, stored: string | null, osDark = false) {
  window.history.replaceState(null, "", path);
  if (stored === null) localStorage.removeItem(THEME_STORAGE_KEY);
  else localStorage.setItem(THEME_STORAGE_KEY, stored);
  stubOsDark(osDark);
  resetHtml();
}

function run(src: string) {
  new Function(src)();
  return { className: html.className, colorScheme: html.style.colorScheme };
}

/** The inline script next-themes 0.4.6 emits for KaziThemeProvider's real props on `path`. */
function nextThemesScript(path: string): string {
  nav.pathname = path;
  const markup = renderToString(
    <KaziThemeProvider>
      <span />
    </KaziThemeProvider>
  );
  const match = markup.match(/<script[^>]*>([\s\S]*?)<\/script>/);
  if (!match) throw new Error("next-themes rendered no script");
  return match[1].replace(/&quot;/g, '"').replace(/&amp;/g, "&");
}

const originalMatchMedia = window.matchMedia;

describe("THEME_INIT_SCRIPT (root layout head)", () => {
  beforeEach(() => resetHtml());

  afterEach(() => {
    vi.unstubAllGlobals();
    window.matchMedia = originalMatchMedia;
    localStorage.removeItem(THEME_STORAGE_KEY);
    window.history.replaceState(null, "", "/");
    resetHtml();
  });

  it("applies a stored dark on a themed route", () => {
    setup("/org/x/dashboard", "dark");
    expect(run(THEME_INIT_SCRIPT)).toEqual({ className: "dark", colorScheme: "dark" });
  });

  it.each(["/dashboard", "/", "/mock-login", "/org", "/organisation/x"])(
    "forces light on %s even with dark stored",
    (path) => {
      setup(path, "dark");
      expect(run(THEME_INIT_SCRIPT)).toEqual({ className: "light", colorScheme: "light" });
    }
  );

  it("system + OS dark resolves to dark", () => {
    setup("/org/x", "system", true);
    expect(run(THEME_INIT_SCRIPT)).toEqual({ className: "dark", colorScheme: "dark" });
  });

  it("system + OS light resolves to light", () => {
    setup("/org/x", "system", false);
    expect(run(THEME_INIT_SCRIPT)).toEqual({ className: "light", colorScheme: "light" });
  });

  it("a stored light stays light", () => {
    setup("/org/x", "light", true);
    expect(run(THEME_INIT_SCRIPT)).toEqual({ className: "light", colorScheme: "light" });
  });

  it("no stored value defaults to light, even with OS dark", () => {
    setup("/org/x/dashboard", null, true);
    expect(run(THEME_INIT_SCRIPT)).toEqual({ className: "light", colorScheme: "light" });
  });

  it("a garbage stored value defaults to light", () => {
    setup("/org/x/dashboard", "purple <script>", true);
    expect(run(THEME_INIT_SCRIPT)).toEqual({ className: "light", colorScheme: "light" });
  });

  it("replaces the opposite class instead of stacking both", () => {
    setup("/org/x", "dark");
    html.classList.add("light");
    expect(run(THEME_INIT_SCRIPT).className).toBe("dark");
  });

  it("does not throw when localStorage throws, and paints light", () => {
    setup("/org/x/dashboard", null);
    vi.stubGlobal("localStorage", {
      getItem: () => {
        throw new Error("SecurityError");
      },
    });
    expect(() => run(THEME_INIT_SCRIPT)).not.toThrow();
    expect(html.className).toBe("light");
  });

  it("does not throw when matchMedia is unavailable", () => {
    setup("/org/x", "system");
    window.matchMedia = undefined as unknown as typeof window.matchMedia;
    expect(() => run(THEME_INIT_SCRIPT)).not.toThrow();
  });
});

describe("THEME_INIT_SCRIPT agrees with the provider", () => {
  afterEach(() => {
    window.matchMedia = originalMatchMedia;
    localStorage.removeItem(THEME_STORAGE_KEY);
    window.history.replaceState(null, "", "/");
    resetHtml();
  });

  it.each([
    "/org/acme/dashboard",
    "/org/x",
    "/org",
    "/organisation",
    "/",
    "/dashboard",
    "/mock-login",
  ])("uses the shared forced-light predicate on %s", (path) => {
    setup(path, "dark");
    expect(run(THEME_INIT_SCRIPT).className).toBe(isThemedRoute(path) ? "dark" : "light");
  });

  const cases: Array<[string, string | null, boolean]> = [
    ["/org/acme/dashboard", "dark", false],
    ["/org/acme/dashboard", "light", true],
    ["/org/acme/dashboard", "system", true],
    ["/org/acme/dashboard", "system", false],
    ["/org/acme/dashboard", null, true],
    ["/dashboard", "dark", true],
    ["/", "system", true],
    ["/mock-login", "dark", false],
  ];

  it.each(cases)(
    "matches next-themes' own script on %s with %s stored (OS dark: %s)",
    (path, stored, osDark) => {
      const theirs = nextThemesScript(path);
      setup(path, stored, osDark);
      const expected = run(theirs);
      setup(path, stored, osDark);
      expect(run(THEME_INIT_SCRIPT)).toEqual(expected);
      // Head script then next-themes' body script: next-themes re-applies the same result.
      expect(run(theirs)).toEqual(expected);
    }
  );
});
