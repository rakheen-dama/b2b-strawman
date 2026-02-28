"use client";

import { Search } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * Stub button for the command palette (Cmd+K).
 * Will be wired to cmdk in a later task.
 */
export function CommandPaletteButton() {
  return (
    <Button
      variant="ghost"
      size="sm"
      className="hidden gap-2 text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200 sm:flex"
      onClick={() => {
        // Will open command palette when wired
      }}
    >
      <Search className="h-4 w-4" />
      <span className="text-xs text-slate-400">
        <kbd className="pointer-events-none rounded border border-slate-200 px-1.5 py-0.5 font-mono text-[10px] font-medium text-slate-400 dark:border-slate-700">
          ⌘K
        </kbd>
      </span>
    </Button>
  );
}
