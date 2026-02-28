"use client";

import { Breadcrumbs } from "./breadcrumbs";
import { CommandPaletteButton } from "./command-palette-button";
import { NotificationBell } from "./notification-bell";
import { AuthControls } from "./auth-controls";

interface TopBarProps {
  slug: string;
}

export function TopBar({ slug }: TopBarProps) {
  return (
    <header className="sticky top-0 z-30 flex h-[var(--topbar-height)] items-center gap-4 border-b border-slate-200/60 bg-white/80 px-4 backdrop-blur-md dark:border-slate-800/60 dark:bg-slate-900/80">
      <Breadcrumbs slug={slug} />
      <div className="flex-1" />
      <CommandPaletteButton />
      <NotificationBell />
      <AuthControls />
    </header>
  );
}
