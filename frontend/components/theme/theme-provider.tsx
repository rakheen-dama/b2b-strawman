"use client";

import type { ReactNode } from "react";
import { usePathname } from "next/navigation";
import { ThemeProvider } from "next-themes";
import { DEFAULT_THEME, THEME_STORAGE_KEY, isThemedRoute } from "@/components/theme/theme-config";

interface KaziThemeProviderProps {
  children: ReactNode;
}

/**
 * Class-strategy theme provider: light by default, opt-in Dark / System per browser. Lives
 * inside `<body>` because `AuthProvider` wraps `<html>`, so next-themes' inline script is emitted
 * in `<body>` too and a streamed HTML chunk can end before it executes (a light frame on a cold
 * cache). The first paint is therefore owned by `THEME_INIT_SCRIPT` in the root layout's `<head>`
 * (`app/layout.tsx`); this provider owns the theme at runtime. Both read `theme-config.ts`.
 */
export function KaziThemeProvider({ children }: KaziThemeProviderProps) {
  const pathname = usePathname();
  return (
    <ThemeProvider
      attribute="class"
      defaultTheme={DEFAULT_THEME}
      enableSystem
      storageKey={THEME_STORAGE_KEY}
      disableTransitionOnChange
      forcedTheme={isThemedRoute(pathname) ? undefined : DEFAULT_THEME}
    >
      {children}
    </ThemeProvider>
  );
}
