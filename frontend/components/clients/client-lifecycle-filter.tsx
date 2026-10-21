import Link from "next/link";
import { cn } from "@/lib/utils";
import type { LifecycleStatus } from "@/lib/types";

const LIFECYCLE_PILLS: ReadonlyArray<{ value: LifecycleStatus | null; label: string }> = [
  { value: null, label: "All" },
  { value: "PROSPECT", label: "Prospect" },
  { value: "ONBOARDING", label: "Onboarding" },
  { value: "ACTIVE", label: "Active" },
  { value: "DORMANT", label: "Dormant" },
  { value: "OFFBOARDING", label: "Offboarding" },
  { value: "OFFBOARDED", label: "Offboarded" },
];

const PILL_CLASS = "rounded-full px-3 py-1 text-sm font-medium transition-colors";
const PILL_ACTIVE = "bg-slate-900 text-slate-50 dark:bg-slate-100 dark:text-slate-900";
const PILL_IDLE =
  "bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-400 dark:hover:bg-slate-700";

interface ClientLifecycleFilterProps {
  slug: string;
  /** Current URL params (strings only); every pill keeps the others (view, search, sort, tags). */
  query: Record<string, string>;
  /** The lifecycle the list is filtered to (after the URL override), or null for all. */
  activeLifecycle: LifecycleStatus | null;
  /**
   * The selected system view's own lifecycle (the "Active" view's ACTIVE). "All" then writes
   * `lifecycleStatus=ALL` to lift it; otherwise "All" removes the param.
   */
  viewLifecycle: LifecycleStatus | null;
  /**
   * D-2: lifecycle pills only on system views. The backend ignores `lifecycleStatus` on the
   * saved-view path, so there they would filter nothing.
   */
  showLifecycle: boolean;
  showIncomplete: boolean;
}

function href(slug: string, query: Record<string, string>, set: Record<string, string | null>) {
  const params = new URLSearchParams(query);
  for (const [key, value] of Object.entries(set)) {
    if (value === null) params.delete(key);
    else params.set(key, value);
  }
  const qs = params.toString();
  return `/org/${slug}/clients${qs ? `?${qs}` : ""}`;
}

/**
 * Clients toolbar pills (615D, D-2 / D-3): lifecycle status, then the "Show incomplete" toggle
 * that `incomplete-profiles-widget` links to (`?showIncomplete=true`). Directive-free: plain
 * links built from the server page's params.
 */
export function ClientLifecycleFilter({
  slug,
  query,
  activeLifecycle,
  viewLifecycle,
  showLifecycle,
  showIncomplete,
}: ClientLifecycleFilterProps) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {showLifecycle && (
        <>
          <div
            role="group"
            aria-label="Lifecycle"
            className="flex flex-wrap items-center gap-2"
            data-testid="client-lifecycle-filter"
          >
            {LIFECYCLE_PILLS.map((pill) => {
              const isActive = pill.value === activeLifecycle;
              // null drops the param.
              const target = pill.value ?? (viewLifecycle !== null ? "ALL" : null);
              return (
                <Link
                  key={pill.label}
                  href={href(slug, query, { lifecycleStatus: target })}
                  aria-current={isActive ? "true" : undefined}
                  className={cn(PILL_CLASS, isActive ? PILL_ACTIVE : PILL_IDLE)}
                >
                  {pill.label}
                </Link>
              );
            })}
          </div>
          <span aria-hidden="true" className="mx-1 h-4 w-px bg-slate-300 dark:bg-slate-700" />
        </>
      )}
      <Link
        href={href(slug, query, { showIncomplete: showIncomplete ? null : "true" })}
        aria-current={showIncomplete ? "true" : undefined}
        data-testid="client-show-incomplete"
        className={cn(PILL_CLASS, showIncomplete ? PILL_ACTIVE : PILL_IDLE)}
      >
        Show incomplete
      </Link>
    </div>
  );
}
