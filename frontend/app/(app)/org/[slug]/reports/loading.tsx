import { Skeleton } from "@/components/ui/skeleton";

export default function ReportsLoading() {
  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <Skeleton className="h-9 w-32" />
      </div>

      {/* Category Section Skeleton */}
      {Array.from({ length: 2 }).map((_, sectionIdx) => (
        <div key={sectionIdx} className="space-y-4">
          <Skeleton className="h-7 w-40" />
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 3 }).map((_, cardIdx) => (
              <div
                key={cardIdx}
                className="flex flex-col gap-4 rounded-lg border border-slate-200/80 p-6 dark:border-slate-800/80"
              >
                <Skeleton className="h-5 w-3/4" />
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-1/3" />
              </div>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
