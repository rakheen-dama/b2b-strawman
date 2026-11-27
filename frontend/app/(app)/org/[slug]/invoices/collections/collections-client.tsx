import { CircleCheck } from "lucide-react";
import type { AiGateListItem } from "@/lib/api/ai";
import type { DebtorResponse } from "@/lib/api/collections";
import { DebtorBookTable } from "@/components/collections/debtor-book-table";
import { EmptyState } from "@/components/empty-state";
import { ReminderQueue, type ReminderPreview } from "./reminder-queue";

interface CollectionsClientProps {
  slug: string;
  debtors: DebtorResponse[];
  /** Only populated for AI_REVIEW holders; when null the queue section is hidden. */
  gates: AiGateListItem[] | null;
  previews: Record<string, ReminderPreview>;
}

/**
 * The Debtors page body (a Server Component despite its name): the Debtor book (a client
 * `DataTable`, first page of 50, no pager) and the AI_REVIEW holders' pending-reminder queue.
 */
export function CollectionsClient({ slug, debtors, gates, previews }: CollectionsClientProps) {
  return (
    <div className="space-y-10">
      {/* Debtor book */}
      <section className="space-y-4">
        <div>
          <h2 className="font-display text-foreground text-xl">Debtor book</h2>
          <p className="text-muted-foreground mt-1 text-sm">
            Outstanding balances grouped by client and currency, aged into buckets.
          </p>
        </div>

        <DebtorBookTable
          slug={slug}
          debtors={debtors}
          empty={
            <EmptyState
              variant="compact"
              icon={CircleCheck}
              title="No outstanding balances."
              description="Everything is settled."
            />
          }
        />
      </section>

      {/* Pending-reminder queue — AI_REVIEW holders only */}
      {gates !== null && (
        <section className="space-y-4">
          <div>
            <h2 className="font-display text-foreground text-xl">Pending reminders</h2>
            <p className="text-muted-foreground mt-1 text-sm">
              Review the drafted reminders before they are sent to clients.
            </p>
          </div>
          <ReminderQueue slug={slug} gates={gates} previews={previews} />
        </section>
      )}
    </div>
  );
}
