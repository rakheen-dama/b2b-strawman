import type { ReactNode } from "react";

// Shared bits of the Home widgets (614B): the header/footer link style, the quiet error line and
// the headline figure. Deliberately not a widget shell — each widget still owns its Card.

export const WIDGET_LINK_CLASS =
  "text-xs text-slate-500 hover:text-slate-900 dark:hover:text-slate-100";

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
      className="font-mono text-2xl font-semibold text-slate-950 tabular-nums dark:text-slate-50"
    >
      {children}
    </p>
  );
}
