import { Skeleton } from "@b2mash/ui/skeleton";

export default function ClientsLoading() {
  return (
    <div className="grid grid-cols-1 gap-6">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <Skeleton className="h-9 w-32" />
          <Skeleton className="mt-2 h-4 w-20" />
        </div>
        <Skeleton className="h-9 w-28 rounded-full" />
      </div>

      {/* Toolbar: view tabs, then search + lifecycle pills + filters */}
      <div className="space-y-3">
        <div className="flex items-center gap-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-6 w-24" />
          ))}
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Skeleton className="h-9 w-64" />
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-7 w-20 rounded-full" />
          ))}
        </div>
      </div>

      {/* Table */}
      <div className="rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
        <div className="flex items-center gap-6 border-b border-slate-200 px-2 py-3 dark:border-slate-800">
          {Array.from({ length: 7 }).map((_, i) => (
            <Skeleton key={i} className="h-3 w-16" />
          ))}
        </div>
        {Array.from({ length: 8 }).map((_, i) => (
          <div
            key={i}
            className="flex items-center gap-6 border-b border-slate-100 px-2 py-3 last:border-0 dark:border-slate-900"
          >
            <Skeleton className="h-4 w-48" />
            <Skeleton className="h-5 w-20 rounded-full" />
            <Skeleton className="h-5 w-16 rounded-full" />
            <Skeleton className="h-4 w-28" />
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-5 w-12 rounded-full" />
            <Skeleton className="h-4 w-20" />
          </div>
        ))}
      </div>
    </div>
  );
}
