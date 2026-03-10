"use client";

import { createContext, useContext, useState, useEffect } from "react";
import { CommandPaletteDialog } from "@/components/command-palette-dialog";

interface CommandPaletteContextValue {
  open: boolean;
  setOpen: (open: boolean) => void;
}

const CommandPaletteContext = createContext<CommandPaletteContextValue | null>(null);

export function useCommandPalette(): CommandPaletteContextValue {
  const ctx = useContext(CommandPaletteContext);
  if (!ctx) {
    throw new Error("useCommandPalette must be used within a CommandPaletteProvider");
  }
  return ctx;
}

interface CommandPaletteProviderProps {
  slug: string;
  children: React.ReactNode;
}

export function CommandPaletteProvider({ slug, children }: CommandPaletteProviderProps) {
  const [open, setOpen] = useState(false);

  // Global ⌘K / Ctrl+K keyboard shortcut
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        setOpen((prev) => !prev);
      }
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, []);

  return (
    <CommandPaletteContext.Provider value={{ open, setOpen }}>
      {children}
      <CommandPaletteDialog slug={slug} open={open} onOpenChange={setOpen} />
    </CommandPaletteContext.Provider>
  );
}
