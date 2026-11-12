"use client";

import type { ReactNode } from "react";
import { usePathname } from "next/navigation";
import { ThemeProvider } from "next-themes";

/** localStorage key of the per-browser theme choice (ADR-371). */
export const THEME_STORAGE_KEY = "kazi-theme";

/** Only the org-scoped app is themed; every other route always paints light (ADR-371, B6). */
export function isThemedRoute(pathname: string | null): boolean {
  return pathname?.startsWith("/org/") ?? false;
}

interface KaziThemeProviderProps {
  children: ReactNode;
}

/**
 * Class-strategy theme provider: light by default, opt-in Dark / System per browser. Lives
 * inside `<body>` because `AuthProvider` wraps `<html>`; next-themes' blocking script writes the
 * class to `document.documentElement` itself, so the first paint is already in the right theme.
 */
export function KaziThemeProvider({ children }: KaziThemeProviderProps) {
  const pathname = usePathname();
  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="light"
      enableSystem
      storageKey={THEME_STORAGE_KEY}
      disableTransitionOnChange
      forcedTheme={isThemedRoute(pathname) ? undefined : "light"}
    >
      {children}
    </ThemeProvider>
  );
}
