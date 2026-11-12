"use client";

import { useTheme } from "next-themes";
import { Toaster } from "sonner";

/**
 * The app's single sonner Toaster, following the effective theme (ADR-371).
 * `resolvedTheme` ignores `forcedTheme`, so forced-light routes must check it first.
 */
export function ThemedToaster() {
  const { forcedTheme, resolvedTheme } = useTheme();
  const effective = forcedTheme ?? resolvedTheme;
  return <Toaster position="bottom-right" theme={effective === "dark" ? "dark" : "light"} />;
}
