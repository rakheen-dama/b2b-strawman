import type { ComponentType, ReactNode } from "react";

interface IntegrationSectionTitleProps {
  icon: ComponentType<{ className?: string }>;
  children: ReactNode;
}

/**
 * The icon tile + label of an integration card, passed as a `SettingsSection` `title` (665F, DA-3).
 * The tile is `aria-hidden`, so the heading's accessible name stays exactly the label. Server-safe.
 */
export function IntegrationSectionTitle({ icon: Icon, children }: IntegrationSectionTitleProps) {
  return (
    <span className="flex items-center gap-3">
      <span
        aria-hidden="true"
        className="bg-muted flex size-10 shrink-0 items-center justify-center rounded-lg"
      >
        <Icon className="text-muted-foreground size-5" />
      </span>
      {children}
    </span>
  );
}
