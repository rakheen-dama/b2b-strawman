import type { ReactNode } from "react";
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
} from "@b2mash/ui/card";
import { cn } from "@/lib/utils";

/**
 * Settings layout primitives (Phase 91 664A, ADR-394). Layout only: they own no state, submit
 * nothing and add no confirmation (R4), so a per-card save, a page-level save, an instant toggle
 * and a destructive action can sit side by side — each consumer keeps its own save model. They add
 * no visible words (COPY-FREEZE): every title, description and button comes from the consumer.
 * Server-safe (no hooks, no `"use client"`).
 */

/** A slot has content unless it is empty (`null`/`undefined`), a boolean (`cond && <X/>`) or `""`. */
function hasContent(node: ReactNode) {
  return node != null && typeof node !== "boolean" && node !== "";
}

export interface SettingsSectionProps {
  /** Rendered as a real heading at `as` (default `h2`); absent → no heading. */
  title?: ReactNode;
  as?: "h2" | "h3";
  description?: ReactNode;
  /** Right-aligned in the header row. */
  actions?: ReactNode;
  children: ReactNode;
  /** Per-card save etc.: right-aligned in a footer with a top border. */
  footer?: ReactNode;
  /** Left side of the footer (e.g. a "Saved" line the consumer already renders). */
  status?: ReactNode;
  /** `destructive`: a destructive frame and header wash; adds no heading text. */
  variant?: "default" | "destructive";
  id?: string;
  className?: string;
  "data-testid"?: string;
}

export function SettingsSection({
  title,
  as: Heading = "h2",
  description,
  actions,
  children,
  footer,
  status,
  variant = "default",
  id,
  className,
  "data-testid": testId,
}: SettingsSectionProps) {
  const destructive = variant === "destructive";
  const hasHeader = hasContent(title) || hasContent(description) || hasContent(actions);
  const hasFooter = hasContent(footer) || hasContent(status);

  return (
    <Card
      data-slot="settings-section"
      data-variant={variant}
      id={id}
      data-testid={testId}
      className={cn(
        // Card hard-codes a slate border in both themes, so the destructive frame overrides both.
        destructive && "border-destructive/40 dark:border-destructive/40 overflow-hidden",
        destructive && hasHeader && "pt-0",
        className
      )}
    >
      {hasHeader && (
        <CardHeader
          className={cn(destructive && "bg-destructive/5 border-destructive/20 border-b pt-6")}
        >
          {hasContent(title) && (
            <Heading
              className={cn(
                "leading-none font-semibold",
                Heading === "h2" ? "text-lg" : "text-base"
              )}
            >
              {title}
            </Heading>
          )}
          {hasContent(description) && <CardDescription>{description}</CardDescription>}
          {hasContent(actions) && <CardAction>{actions}</CardAction>}
        </CardHeader>
      )}
      <CardContent>{children}</CardContent>
      {hasFooter && (
        <CardFooter className="gap-3 border-t">
          {hasContent(status) && (
            <div className="text-muted-foreground mr-auto text-sm">{status}</div>
          )}
          {hasContent(footer) && <div className="ml-auto flex items-center gap-2">{footer}</div>}
        </CardFooter>
      )}
    </Card>
  );
}

export interface SettingsActionBarProps {
  /** The page-level actions (e.g. one Save for a form spanning several sections). */
  children: ReactNode;
  /** Left side of the bar. */
  status?: ReactNode;
  /** Off by default; a slice that enables it records why. */
  sticky?: boolean;
}

/** A page-level action row, right-aligned, for a save that spans several sections. */
export function SettingsActionBar({ children, status, sticky = false }: SettingsActionBarProps) {
  return (
    <div
      data-slot="settings-action-bar"
      data-sticky={sticky || undefined}
      className={cn(
        "flex items-center justify-end gap-3",
        sticky && "bg-background/95 sticky bottom-0 z-10 border-t py-3 backdrop-blur"
      )}
    >
      {hasContent(status) && <div className="text-muted-foreground mr-auto text-sm">{status}</div>}
      {children}
    </div>
  );
}
