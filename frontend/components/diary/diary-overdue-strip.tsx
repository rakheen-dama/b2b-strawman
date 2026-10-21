import { Card } from "@b2mash/ui/card";
import type { DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";
import { DiaryEventRow } from "./diary-event-row";

/**
 * The overdue band (open items dated before the window) as its own strip above the Day, Week and
 * Month views — never inside a day. Rows show their date because they sit outside any day.
 */
export function DiaryOverdueStrip({ band, slug }: { band: DiaryEventResponse[]; slug: string }) {
  if (band.length === 0) return null;
  return (
    <section aria-labelledby="diary-overdue-strip">
      <Card className="gap-0 py-0">
        <h2
          id="diary-overdue-strip"
          className="text-destructive border-b border-slate-200/80 px-3 py-2 text-sm font-semibold dark:border-slate-800/80"
        >
          Overdue
        </h2>
        <div className="grid grid-cols-1 gap-x-4 gap-y-1 p-2 md:grid-cols-2 xl:grid-cols-3">
          {band.map((event) => (
            <DiaryEventRow key={event.id} event={event} slug={slug} showDate />
          ))}
        </div>
      </Card>
    </section>
  );
}
