import Link from "next/link";
import { TAB_UNDERLINE_CURRENT } from "@/components/shared/tab-underline";

interface FeeNotesSubNavProps {
  slug: string;
  current: "fee-notes" | "fee-runs";
}

/**
 * Fee notes | Fee runs links (Phase 91 666A). They stay links (a list of links, not a tablist); the
 * active one carries `aria-current="page"` (P-8), which drives the shared underline.
 */
export function FeeNotesSubNav({ slug, current }: FeeNotesSubNavProps) {
  return (
    <div className="border-border flex gap-2 border-b">
      <Link
        href={`/org/${slug}/invoices`}
        aria-current={current === "fee-notes" ? "page" : undefined}
        className={TAB_UNDERLINE_CURRENT}
      >
        Fee notes
      </Link>
      <Link
        href={`/org/${slug}/invoices/billing-runs`}
        aria-current={current === "fee-runs" ? "page" : undefined}
        className={TAB_UNDERLINE_CURRENT}
      >
        Fee runs
      </Link>
    </div>
  );
}
