import { Skeleton } from "@b2mash/ui/skeleton";

/** Skeleton shaped like Home's widget rows (614B): header, then 12-column rows of compact cards. */
const ROWS: ReadonlyArray<ReadonlyArray<string>> = [
  ["lg:col-span-6", "lg:col-span-6"],
  ["lg:col-span-6", "lg:col-span-6"],
  ["lg:col-span-4", "lg:col-span-4", "lg:col-span-4"],
];

export default function DashboardLoading() {
  return (
    <div className="space-y-6">
      <div>
        <Skeleton className="h-9 w-32" />
        <Skeleton className="mt-2 h-4 w-48" />
      </div>

      {ROWS.map((row, r) => (
        <div key={r} className="grid grid-cols-1 gap-4 lg:grid-cols-12">
          {row.map((span, c) => (
            <div
              key={c}
              className={`${span} bg-card rounded-lg border border-slate-200/80 p-6 dark:border-slate-800/80`}
            >
              <div className="flex items-center justify-between">
                <Skeleton className="h-4 w-28" />
                <Skeleton className="h-3 w-20" />
              </div>
              <Skeleton className="mt-4 h-7 w-24" />
              <div className="mt-4 space-y-2">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-8 w-full" />
                ))}
              </div>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
