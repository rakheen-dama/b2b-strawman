"use client";

import { useState, useEffect, startTransition } from "react";
import { Sun, Moon, Monitor } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";

type ThemeMode = "light" | "dark" | "system";

const STORAGE_KEY = "theme";
const SYSTEM_PREFERENCE_QUERY = "(prefers-color-scheme: dark)";

/**
 * Detects if the system prefers dark mode
 */
function getSystemPreference(): boolean {
  if (typeof window === "undefined") return false;
  return window.matchMedia(SYSTEM_PREFERENCE_QUERY).matches;
}

/**
 * Applies the dark class to the html element based on the resolved theme
 */
function applyTheme(mode: ThemeMode): void {
  if (typeof document === "undefined") return;

  const html = document.documentElement;
  const isDark =
    mode === "dark" || (mode === "system" && getSystemPreference());

  if (isDark) {
    html.classList.add("dark");
  } else {
    html.classList.remove("dark");
  }
}

/**
 * Gets the persisted theme preference from localStorage
 */
function getStoredTheme(): ThemeMode | null {
  if (typeof window === "undefined") return null;
  const stored = localStorage.getItem(STORAGE_KEY);
  if (stored === "light" || stored === "dark" || stored === "system") {
    return stored as ThemeMode;
  }
  return null;
}

/**
 * Persists theme preference to localStorage
 */
function persistTheme(mode: ThemeMode): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(STORAGE_KEY, mode);
}

/**
 * ThemeToggle component with dropdown menu for light/dark/system modes
 * Persists preference to localStorage and applies .dark class to <html>
 */
export function ThemeToggle() {
  const [theme, setTheme] = useState<ThemeMode>("system");
  const [mounted, setMounted] = useState(false);

  // Initialize theme on mount
  useEffect(() => {
    const stored = getStoredTheme();
    const initialTheme = stored || "system";
    startTransition(() => {
      setTheme(initialTheme);
      applyTheme(initialTheme);
      setMounted(true);
    });
  }, []);

  // Listen for system preference changes (in system mode)
  useEffect(() => {
    if (!mounted || theme !== "system") return;

    const mediaQuery = window.matchMedia(SYSTEM_PREFERENCE_QUERY);
    const handleChange = () => {
      applyTheme("system");
    };

    mediaQuery.addEventListener("change", handleChange);
    return () => mediaQuery.removeEventListener("change", handleChange);
  }, [mounted, theme]);

  const handleThemeChange = (newTheme: ThemeMode) => {
    setTheme(newTheme);
    persistTheme(newTheme);
    applyTheme(newTheme);
  };

  // Don't render until hydrated to avoid hydration mismatch
  if (!mounted) {
    return (
      <Button
        variant="ghost"
        size="sm"
        disabled
        className="h-8 w-8 p-0"
      />
    );
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
          {theme === "light" && (
            <Sun className="h-4 w-4" />
          )}
          {theme === "dark" && (
            <Moon className="h-4 w-4" />
          )}
          {theme === "system" && (
            <Monitor className="h-4 w-4" />
          )}
          <span className="sr-only">Toggle theme</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={() => handleThemeChange("light")}>
          <Sun className="mr-2 h-4 w-4" />
          Light
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => handleThemeChange("dark")}>
          <Moon className="mr-2 h-4 w-4" />
          Dark
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => handleThemeChange("system")}>
          <Monitor className="mr-2 h-4 w-4" />
          System
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
