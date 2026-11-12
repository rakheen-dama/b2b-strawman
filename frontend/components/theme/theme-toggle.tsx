"use client";

import { useSyncExternalStore } from "react";
import { Monitor, Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";

import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { cn } from "@/lib/utils";

const THEME_OPTIONS = [
  { value: "light", label: "Light", Icon: Sun },
  { value: "dark", label: "Dark", Icon: Moon },
  { value: "system", label: "System", Icon: Monitor },
] as const;

type ThemeChoice = (typeof THEME_OPTIONS)[number]["value"];

function isThemeChoice(value: string): value is ThemeChoice {
  return THEME_OPTIONS.some((option) => option.value === value);
}

const subscribeNoop = () => () => {};

/**
 * `false` on the server and during hydration, `true` afterwards. Avoids the setState-in-effect
 * mount pattern (`react-hooks/set-state-in-effect` is an error in this repo).
 */
function useHasMounted(): boolean {
  return useSyncExternalStore(
    subscribeNoop,
    () => true,
    () => false
  );
}

interface ThemeToggleProps {
  /** Only "segmented" exists (B5); kept as a prop so a later menu variant is additive. */
  variant?: "segmented";
  className?: string;
}

/**
 * Light / Dark / System segmented control. The checked item follows the stored choice (`theme`),
 * not `resolvedTheme`, so System shows as selected. Renders a same-size placeholder until mounted
 * because the stored choice is unknown on the server.
 */
export function ThemeToggle({ className }: ThemeToggleProps) {
  const mounted = useHasMounted();
  const { theme, setTheme } = useTheme();

  if (!mounted) {
    return (
      <div
        aria-hidden="true"
        data-slot="theme-toggle-placeholder"
        className={cn("bg-muted h-8 w-full rounded-md", className)}
      />
    );
  }

  return (
    <ToggleGroup
      type="single"
      variant="outline"
      size="sm"
      aria-label="Theme"
      value={theme ?? "light"}
      // Radix emits "" when the active item is clicked again; a theme is always chosen, so ignore it.
      onValueChange={(value) => {
        if (isThemeChoice(value)) setTheme(value);
      }}
      className={cn("w-full", className)}
    >
      {THEME_OPTIONS.map(({ value, label, Icon }) => (
        <ToggleGroupItem key={value} value={value} className="flex-1 gap-1 px-2 text-xs">
          <Icon aria-hidden="true" className="size-3.5" />
          {label}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  );
}
