"use client";

import { useTheme } from "next-themes";
import { Toaster } from "sonner";

/** The app's single sonner Toaster, following the resolved theme (ADR-371). */
export function ThemedToaster() {
  const { resolvedTheme } = useTheme();
  return <Toaster position="bottom-right" theme={resolvedTheme === "dark" ? "dark" : "light"} />;
}
