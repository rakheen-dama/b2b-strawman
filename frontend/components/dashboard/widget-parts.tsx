import type { ComponentProps, ReactNode } from "react";
import { Card, CardAction, CardContent, CardFooter, CardHeader, CardTitle } from "@b2mash/ui/card";
import { cn } from "@/lib/utils";

// Shared bits of the Home widgets (614B; on the kit in 640B): the widget card shell (one density
// and elevation for all nine), the header/footer link style, the quiet error line, the headline
// figure, the list-row style and the status badge. Server-safe — no "use client", no hooks: server
// widgets import it.

/** Home card elevation (arch §15.2.5) — also for kit cards used as a widget (StatCard). */
export const WIDGET_ELEVATION_CLASS = "shadow-(--shadow-card)";
/** Home card density + elevation: p-5 cards (px-5 on the sections), gap-4. */
export const WIDGET_CARD_CLASS = `gap-4 py-5 ${WIDGET_ELEVATION_CLASS}`;

export const WIDGET_LINK_CLASS =
  "text-muted-foreground hover:text-foreground focus-visible:ring-ring rounded-sm text-xs outline-none focus-visible:ring-2";

/** A clickable list row inside a widget (task, matter, client, event): hover wash + focus ring. */
export const WIDGET_ROW_CLASS =
  "hover:bg-muted focus-visible:ring-ring flex items-center gap-2 rounded-md px-2 py-1.5 outline-none focus-visible:ring-2";

interface WidgetCardProps extends Omit<ComponentProps<"div">, "title"> {
  title: ReactNode;
  /** Header links (CardAction). */
  action?: ReactNode;
  footer?: ReactNode;
  /** Props of the CardContent (e.g. a testid or `@container`). */
  contentProps?: ComponentProps<"div"> & { "data-testid"?: string };
}

/** The widget card: title + action header, content, optional footer; `...rest` reaches the root. */
export function WidgetCard({
  title,
  action,
  footer,
  contentProps,
  className,
  children,
  ...rest
}: WidgetCardProps) {
  return (
    <Card className={cn(WIDGET_CARD_CLASS, className)} {...rest}>
      <CardHeader className="px-5">
        <CardTitle className="text-foreground flex items-center gap-2 text-sm font-medium">
          {title}
        </CardTitle>
        {action ? <CardAction className="flex items-center gap-3">{action}</CardAction> : null}
      </CardHeader>
      <CardContent {...contentProps} className={cn("px-5", contentProps?.className)}>
        {children}
      </CardContent>
      {footer ? <CardFooter className="px-5">{footer}</CardFooter> : null}
    </Card>
  );
}

/** The card's quiet error line, e.g. `subject="Debtors"` → "Debtors could not be loaded. …". */
export function WidgetErrorLine({ subject }: { subject: string }) {
  return (
    <p className="text-muted-foreground text-sm italic">
      {subject} could not be loaded. Refresh to try again.
    </p>
  );
}

/** The widget's headline figure (count, amount or duration). */
export function WidgetHeadline({ testId, children }: { testId: string; children: ReactNode }) {
  return (
    <p
      data-testid={testId}
      className="text-foreground font-mono text-2xl font-semibold tabular-nums"
    >
      {children}
    </p>
  );
}

export type StatusTone = "success" | "warning" | "danger" | "info" | "neutral";

const TONE_CLASS: Record<StatusTone, string> = {
  success: "bg-success-soft text-success-foreground",
  warning: "bg-warning-soft text-warning-foreground",
  danger: "bg-danger-soft text-danger-foreground",
  info: "bg-info-soft text-info-foreground",
  neutral: "bg-muted text-muted-foreground",
};

interface StatusBadgeProps extends ComponentProps<"span"> {
  tone: StatusTone;
  /** A rendered lucide icon (`<CheckCircle2 />`); decorative — the label carries the status. */
  icon: ReactNode;
}

/** Status = token + icon + label (ADR-370): colour is never the only signal. */
export function StatusBadge({ tone, icon, className, children, ...rest }: StatusBadgeProps) {
  return (
    <span
      data-tone={tone}
      className={cn(
        "inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap [&>svg]:size-3 [&>svg]:shrink-0",
        TONE_CLASS[tone],
        className
      )}
      {...rest}
    >
      {icon}
      {children}
    </span>
  );
}
