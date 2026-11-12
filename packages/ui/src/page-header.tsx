import * as React from "react";

import { cn } from "./cn";

interface PageHeaderProps {
  /** Rendered inside the page's single <h1>. */
  title: React.ReactNode;
  /** One muted line under the title. */
  description?: React.ReactNode;
  /** Above the title (e.g. a module label). Never a heading. */
  eyebrow?: React.ReactNode;
  /** Slot only — the app shell already renders Breadcrumbs; in-scope pages leave it empty. */
  breadcrumb?: React.ReactNode;
  /** Right-aligned; primary first, overflow menu last; wraps below the title under 768px. */
  actions?: React.ReactNode;
  /** Toolbar row under the title (filters, view selector). */
  children?: React.ReactNode;
  className?: string;
  "data-testid"?: string;
}

/**
 * Page title block (Phase 90 §15.2.2). Server-safe: no hooks, no framework imports.
 * Renders exactly one <h1>; the root is a <div>, not a <header>, so it never becomes a second
 * `banner` landmark beside the app shell's header.
 */
function PageHeader({
  title,
  description,
  eyebrow,
  breadcrumb,
  actions,
  children,
  className,
  "data-testid": testId = "page-header",
}: PageHeaderProps) {
  return (
    <div
      data-slot="page-header"
      data-testid={testId}
      className={cn("flex flex-col gap-4", className)}
    >
      {breadcrumb ? <div data-slot="page-header-breadcrumb">{breadcrumb}</div> : null}
      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between md:gap-6">
        <div className="min-w-0 space-y-1">
          {eyebrow ? (
            <p
              data-slot="page-header-eyebrow"
              className="text-muted-foreground text-xs font-medium"
            >
              {eyebrow}
            </p>
          ) : null}
          <h1
            data-slot="page-header-title"
            className="font-display text-foreground text-2xl font-semibold tracking-tight"
          >
            {title}
          </h1>
          {description ? (
            <p data-slot="page-header-description" className="text-muted-foreground text-sm">
              {description}
            </p>
          ) : null}
        </div>
        {actions ? (
          <div
            data-slot="page-header-actions"
            className="flex shrink-0 flex-wrap items-center gap-2"
          >
            {actions}
          </div>
        ) : null}
      </div>
      {children ? (
        <div data-slot="page-header-toolbar" className="flex flex-wrap items-center gap-2">
          {children}
        </div>
      ) : null}
    </div>
  );
}

export { PageHeader, type PageHeaderProps };
