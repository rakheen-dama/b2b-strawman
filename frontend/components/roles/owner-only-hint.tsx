import { cn } from "@/lib/utils";
import { OWNER_ONLY_GRANT_HINT } from "@/lib/capabilities";

/**
 * FR-4: the visible hint on an owner-only capability, or on a role holding one, when the caller is
 * not the owner and so cannot grant it. A `span` so it may sit inside a select option.
 */
export function OwnerOnlyHint({ testId, className }: { testId: string; className?: string }) {
  return (
    <span
      data-testid={testId}
      className={cn("block text-xs text-slate-500 dark:text-slate-400", className)}
    >
      {OWNER_ONLY_GRANT_HINT}
    </span>
  );
}
