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
  /** Required at every call site by `__tests__/emptystate-variant-guard.test.ts` (ADR-393); see the rule on `EmptyState`. */
  variant?: EmptyStateVariant;
}

// No "use client": RSC importers pass `icon` as a component reference, which only works while
// this module renders in whichever environment imports it.
/**
 * Sizing rule (Phase 91, ADR-393). Every call site passes `variant` explicitly — enforced by
 * `__tests__/emptystate-variant-guard.test.ts`; the `"default"` fallback stays only until 669B
 * makes the prop required.
 *
 * | variant   | use when | markup |
 * | --------- | -------- | ------ |
 * | `default` | the empty state IS the whole content of a page or of a page-level tab panel | `gap-3 py-16`, 48px icon tile, `h2` title |
 * | `compact` | inside a section, card, widget, chart card, dialog body or Sheet — at most a one-line description and one optional action; the box stays ≤ 13.25rem (212px) at the container's real width | `gap-2 py-8`, 40px icon tile, `h2` title |
 * | `inline`  | a table body or a single-line slot (one row: icon, title, description, action) | `py-3 text-sm` row; the title is a `<p>`, not a heading |
 *
 * Budget (compact): one-line description 164px; + one action 212px; two lines 184px; two lines +
 * action 232px (over — use `inline`). A title asserted as a heading (e.g. e2e) must stay
 * `default`/`compact`.
 */
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
        className="text-primary text-sm underline underline-offset-4"
      >
        {secondaryLink.label}
      </a>
    ) : (
      <Link href={secondaryLink.href} className="text-primary text-sm underline underline-offset-4">
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
