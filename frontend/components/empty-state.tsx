// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): default-layout/src/views/pages/empty-state/empty-state-v2/.
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
import Link from "next/link";
import type { LucideIcon } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { cn } from "@/lib/utils";

type EmptyStateVariant = "default" | "compact" | "inline";

interface EmptyStateProps {
  icon: LucideIcon;
  title: React.ReactNode;
  description: React.ReactNode;
  action?: React.ReactNode;
  actionLabel?: string;
  actionHref?: string;
  onAction?: () => void;
  secondaryLink?: { label: string; href: string };
  /** default = page-level; compact = widgets and chart cards; inline = one line in a table body. */
  variant?: EmptyStateVariant;
}

// No "use client": RSC importers pass `icon` as a component reference, which only works while
// this module renders in whichever environment imports it.
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  actionLabel,
  actionHref,
  onAction,
  secondaryLink,
  variant = "default",
}: EmptyStateProps) {
  const inline = variant === "inline";
  const compact = variant === "compact";

  const actionButton =
    actionLabel && onAction ? (
      <Button size="sm" variant="outline" onClick={onAction}>
        {actionLabel}
      </Button>
    ) : actionLabel && actionHref ? (
      <Button asChild size="sm" variant="outline">
        <Link href={actionHref}>{actionLabel}</Link>
      </Button>
    ) : null;

  const hasActions = Boolean(actionButton || action);

  const secondary = secondaryLink ? (
    secondaryLink.href.startsWith("http") ? (
      <a
        href={secondaryLink.href}
        target="_blank"
        rel="noopener noreferrer"
        className="text-sm text-teal-600 hover:text-teal-700"
      >
        {secondaryLink.label}
      </a>
    ) : (
      <Link href={secondaryLink.href} className="text-sm text-teal-600 hover:text-teal-700">
        {secondaryLink.label}
      </Link>
    )
  ) : null;

  if (inline) {
    return (
      <div
        data-slot="empty-state"
        data-variant={variant}
        className="text-muted-foreground flex flex-wrap items-center gap-x-2 gap-y-1 py-3 text-sm"
      >
        <Icon aria-hidden="true" className="size-4 shrink-0" />
        <p className="text-foreground font-medium">{title}</p>
        <p>{description}</p>
        {actionButton}
        {action}
        {secondary}
      </div>
    );
  }

  return (
    <div
      data-slot="empty-state"
      data-variant={variant}
      className={cn(
        "flex flex-col items-center text-center",
        compact ? "gap-2 py-8" : "gap-3 py-16"
      )}
    >
      <div
        data-slot="empty-state-icon"
        className={cn(
          "bg-muted text-muted-foreground flex shrink-0 items-center justify-center rounded-lg",
          compact ? "size-10" : "size-12"
        )}
      >
        <Icon aria-hidden="true" className={compact ? "size-5" : "size-6"} />
      </div>
      <h2
        className={cn(
          "font-display text-foreground font-semibold",
          compact ? "text-base" : "text-lg"
        )}
      >
        {title}
      </h2>
      <p className="text-muted-foreground max-w-md text-sm">{description}</p>
      {hasActions && (
        <div className="mt-2 flex flex-wrap items-center justify-center gap-2">
          {actionButton}
          {action}
        </div>
      )}
      {secondary}
    </div>
  );
}
