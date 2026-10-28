"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

/**
 * Entity names for the breadcrumb's crumbs (Phase 88 628B, D11 / U21). `Breadcrumbs` builds its
 * crumbs from the pathname alone, so on a detail page it only knows "Matter" / "Client" /
 * "Fee note". The page already holds the name server-side: it publishes `{ href, label }` here —
 * matter and client pages through `RecordRecentItem`, other detail pages through
 * `<BreadcrumbLabel>` (breadcrumb only, the ⌘K Recent list is untouched) — and `Breadcrumbs`
 * shows that label for the crumb whose href matches. The publisher removes its label on unmount,
 * so another page's crumb is never mislabelled. Until the publisher's effect runs, the crumb shows
 * the generic label (accepted, R-2).
 *
 * Every hook here is a no-op without the provider, so components that publish can render in tests
 * (or trees) that do not mount it.
 */

type SetBreadcrumbLabel = (href: string, label: string) => () => void;

interface BreadcrumbLabelContextValue {
  labels: ReadonlyMap<string, string>;
  setLabel: SetBreadcrumbLabel;
}

const EMPTY_LABELS: ReadonlyMap<string, string> = new Map();
const NOOP_SET: SetBreadcrumbLabel = () => () => {};

const BreadcrumbLabelContext = createContext<BreadcrumbLabelContextValue | null>(null);

export function BreadcrumbLabelProvider({ children }: { children: React.ReactNode }) {
  const [labels, setLabels] = useState<ReadonlyMap<string, string>>(EMPTY_LABELS);

  const setLabel = useCallback<SetBreadcrumbLabel>((href, label) => {
    setLabels((prev) => {
      if (prev.get(href) === label) return prev;
      const next = new Map(prev);
      next.set(href, label);
      return next;
    });
    return () =>
      setLabels((prev) => {
        // Only remove our own label — a newer publisher for the same href may have replaced it.
        if (prev.get(href) !== label) return prev;
        const next = new Map(prev);
        next.delete(href);
        return next;
      });
  }, []);

  const value = useMemo(() => ({ labels, setLabel }), [labels, setLabel]);
  return (
    <BreadcrumbLabelContext.Provider value={value}>{children}</BreadcrumbLabelContext.Provider>
  );
}

/** Published labels by crumb href (`/org/<slug>/…`, no query string). Empty without the provider. */
export function useBreadcrumbLabels(): ReadonlyMap<string, string> {
  return useContext(BreadcrumbLabelContext)?.labels ?? EMPTY_LABELS;
}

/** Publishes `label` for the crumb at `href` while the caller is mounted. No-op without the provider. */
export function usePublishBreadcrumbLabel(href: string, label: string): void {
  const setLabel = useContext(BreadcrumbLabelContext)?.setLabel ?? NOOP_SET;
  useEffect(() => setLabel(href, label), [href, label, setLabel]);
}

interface BreadcrumbLabelProps {
  /** The page's own path, `/org/<slug>/…` — the href `Breadcrumbs` builds for the last crumb. */
  href: string;
  label: string;
}

/** Breadcrumb-only publisher for detail pages that are not in the ⌘K Recent list. Renders nothing. */
export function BreadcrumbLabel({ href, label }: BreadcrumbLabelProps) {
  usePublishBreadcrumbLabel(href, label);
  return null;
}
