"use client";

import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";

interface ShortcutHelpDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

interface Shortcut {
  keys: string[];
  description: string;
  category: "Navigation" | "Creation" | "Editing" | "General";
}

const SHORTCUTS: Shortcut[] = [
  // General
  {
    keys: ["?"],
    description: "Show this help dialog",
    category: "General",
  },
  {
    keys: ["Esc"],
    description: "Close dialogs and overlays",
    category: "General",
  },

  // Creation
  {
    keys: ["c", "p"],
    description: "Create a new project",
    category: "Creation",
  },
  {
    keys: ["c", "t"],
    description: "Create a new task",
    category: "Creation",
  },
  {
    keys: ["c", "i"],
    description: "Create a new invoice",
    category: "Creation",
  },

  // Navigation
  {
    keys: ["j"],
    description: "Navigate to next row in table",
    category: "Navigation",
  },
  {
    keys: ["k"],
    description: "Navigate to previous row in table",
    category: "Navigation",
  },
  {
    keys: ["Enter"],
    description: "Open selected row",
    category: "Navigation",
  },
];

const CATEGORY_ORDER = ["General", "Creation", "Navigation", "Editing"] as const;

export function ShortcutHelpDialog({ open, onOpenChange }: ShortcutHelpDialogProps) {
  const groupedShortcuts = SHORTCUTS.reduce(
    (acc, shortcut) => {
      if (!acc[shortcut.category]) {
        acc[shortcut.category] = [];
      }
      acc[shortcut.category].push(shortcut);
      return acc;
    },
    {} as Record<string, Shortcut[]>
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Keyboard Shortcuts</DialogTitle>
        </DialogHeader>

        <div className="space-y-8">
          {CATEGORY_ORDER.map((category) => {
            const shortcuts = groupedShortcuts[category];
            if (!shortcuts || shortcuts.length === 0) return null;

            return (
              <div key={category}>
                <h3 className="mb-4 text-sm font-semibold text-slate-700 dark:text-slate-300">{category}</h3>
                <div className="space-y-3">
                  {shortcuts.map((shortcut, idx) => (
                    <div key={idx} className="flex items-center justify-between gap-4">
                      <div className="flex flex-wrap gap-2">
                        {shortcut.keys.map((key, keyIdx) => (
                          <div key={keyIdx} className="flex items-center gap-1">
                            <Badge variant="secondary" className="font-mono text-xs">
                              {key.toUpperCase()}
                            </Badge>
                            {keyIdx < shortcut.keys.length - 1 && (
                              <span className="text-xs font-light text-slate-400">+</span>
                            )}
                          </div>
                        ))}
                      </div>
                      <p className="text-sm text-slate-600 dark:text-slate-400">{shortcut.description}</p>
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>

        <div className="mt-6 border-t border-slate-200 pt-4 dark:border-slate-800">
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Shortcuts are disabled inside text input fields and text areas.
          </p>
        </div>
      </DialogContent>
    </Dialog>
  );
}
