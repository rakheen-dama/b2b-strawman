import { Skeleton } from "@/components/ui/skeleton";

export default function ProjectsLoading() {
  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div>
          <Skeleton className="h-9 w-36" />
          <Skeleton className="mt-2 h-4 w-20" />
        </div>
        <Skeleton className="h-9 w-32 rounded-full" />
      </div>

      {/* Projects Grid */}
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 6 }).map((_, i) => (
          <div
            key={i}
            className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950"
          >
            {/* Name + badge */}
            <div className="flex items-center gap-2">
              <Skeleton className="h-5 w-40" />
              <Skeleton className="h-5 w-14 rounded-full" />
            </div>
            {/* Description */}
            <Skeleton className="mt-3 h-4 w-full" />
            <Skeleton className="mt-1 h-4 w-3/4" />
            {/* Meta icons */}
            <div className="mt-4 flex items-center gap-4">
              <Skeleton className="h-4 w-10" />
              <Skeleton className="h-4 w-10" />
              <Skeleton className="h-4 w-20" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
