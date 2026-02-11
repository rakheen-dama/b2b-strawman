import { Skeleton } from "@/components/ui/skeleton";

export default function ProjectDetailLoading() {
  return (
    <div className="space-y-8">
      {/* Back link */}
      <Skeleton className="h-4 w-32" />

      {/* Project Header */}
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-3">
            <Skeleton className="h-8 w-56" />
            <Skeleton className="h-5 w-14 rounded-full" />
          </div>
          <Skeleton className="mt-3 h-4 w-96 max-w-full" />
          <Skeleton className="mt-3 h-4 w-64" />
        </div>
        <div className="flex shrink-0 gap-2">
          <Skeleton className="h-8 w-20 rounded-full" />
        </div>
      </div>

      {/* Tab Bar */}
      <div className="flex gap-6 border-b border-olive-200 dark:border-olive-800">
        <Skeleton className="h-9 w-24" />
        <Skeleton className="h-9 w-24" />
      </div>

      {/* Table Skeleton */}
      <div className="rounded-lg border border-olive-200 bg-white dark:border-olive-800 dark:bg-olive-950">
        <div className="px-6 pt-6 pb-4">
          <Skeleton className="h-5 w-28" />
        </div>
        <div className="px-6 pb-6 space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      </div>
    </div>
  );
}
