// Chart kit figure wrapper (ADR-370): a Card whose accessible name is its visible title, with
// loading / empty / error states at the chart's height. Server-safe — no hooks.
import type { LucideIcon } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@b2mash/ui/card";
import { Skeleton } from "@b2mash/ui/skeleton";
import { EmptyState } from "@/components/empty-state";
import { cn } from "@/lib/utils";
import { ChartSummary } from "./chart-summary";

export type ChartCardState = "ready" | "loading" | "empty" | "error";

/** Default empty-state title of the recharts presets (callers usually pass their own). */
export const DEFAULT_EMPTY_TITLE = "No data for this period.";

/** Props every recharts preset forwards to ChartCard (all serializable). */
export interface ChartCardSlots {
  /** Visible card title and the figure's accessible name. */
  title: string;
  /** Card description — money charts state their currency here. */
  description?: React.ReactNode;
  /** Shows the loading skeleton at chart height. */
  loading?: boolean;
  /** Shows this message instead of the chart (`role="status"`). */
  error?: React.ReactNode;
  emptyTitle?: React.ReactNode;
  emptyDescription?: React.ReactNode;
  /** Rendered below the chart in the ready state (e.g. a consumer's own legend list). */
  footer?: React.ReactNode;
  className?: string;
  "data-testid"?: string;
}

interface ChartCardProps extends Omit<ChartCardSlots, "loading" | "error"> {
  /** Chart area height in px; the skeleton uses the same height (no layout shift). */
  height: number;
  state: ChartCardState;
  /** Chosen by the preset, never passed from an RSC caller. */
  emptyIcon: LucideIcon;
  errorMessage?: React.ReactNode;
  /** One-sentence sr-only summary (ready state). */
  summary?: string;
  /** The "View as table" toggle (ready state). */
  toggle?: React.ReactNode;
  /** The chart (ready state). */
  children: React.ReactNode;
  /**
   * Renders no header: the caller's own card shows the title (640B Unbilled WIP). The figure keeps
   * its accessible name (`aria-label={title}`). Default off.
   */
  hideHeader?: boolean;
}

export function ChartCard({
  title,
  description,
  height,
  state,
  emptyIcon,
  emptyTitle = DEFAULT_EMPTY_TITLE,
  emptyDescription,
  errorMessage,
  summary,
  toggle,
  footer,
  className,
  "data-testid": testId,
  children,
  hideHeader = false,
}: ChartCardProps) {
  return (
    <Card
      role="figure"
      aria-label={title}
      aria-busy={state === "loading" ? true : undefined}
      data-slot="chart-card"
      data-state={state}
      data-testid={testId}
      className={cn("gap-4 py-5", className)}
    >
      {hideHeader ? null : (
        <CardHeader className="px-5">
          <CardTitle className="text-foreground">{title}</CardTitle>
          {description ? <CardDescription>{description}</CardDescription> : null}
        </CardHeader>
      )}
      <CardContent className="px-5">
        {state === "loading" ? (
          <Skeleton className="w-full" style={{ height }} />
        ) : state === "empty" ? (
          <EmptyState
            variant="compact"
            icon={emptyIcon}
            title={emptyTitle}
            description={emptyDescription ?? null}
          />
        ) : state === "error" ? (
          <p role="status" className="text-muted-foreground py-4 text-center text-sm">
            {errorMessage}
          </p>
        ) : (
          <>
            {children}
            {summary ? <ChartSummary>{summary}</ChartSummary> : null}
            {footer}
            {toggle}
          </>
        )}
      </CardContent>
    </Card>
  );
}
