import { Skeleton } from "@/components/ui/skeleton";

export default function CustomersLoading() {
  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Skeleton className="h-9 w-40" />
          <Skeleton className="h-6 w-8 rounded-full" />
        </div>
        <Skeleton className="h-9 w-36 rounded-full" />
      </div>

      {/* Table Skeleton */}
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-olive-200 dark:border-olive-800">
              <th className="px-4 py-3 text-left">
                <Skeleton className="h-3 w-12" />
              </th>
              <th className="px-4 py-3 text-left">
                <Skeleton className="h-3 w-12" />
              </th>
              <th className="hidden px-4 py-3 text-left sm:table-cell">
                <Skeleton className="h-3 w-12" />
              </th>
              <th className="px-4 py-3 text-left">
                <Skeleton className="h-3 w-14" />
              </th>
              <th className="hidden px-4 py-3 text-left lg:table-cell">
                <Skeleton className="h-3 w-16" />
              </th>
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: 5 }).map((_, i) => (
              <tr
                key={i}
                className="border-b border-olive-100 last:border-0 dark:border-olive-800/50"
              >
                <td className="px-4 py-3">
                  <Skeleton className="h-5 w-32" />
                </td>
                <td className="px-4 py-3">
                  <Skeleton className="h-4 w-44" />
                </td>
                <td className="hidden px-4 py-3 sm:table-cell">
                  <Skeleton className="h-4 w-28" />
                </td>
                <td className="px-4 py-3">
                  <Skeleton className="h-5 w-16 rounded-full" />
                </td>
                <td className="hidden px-4 py-3 lg:table-cell">
                  <Skeleton className="h-4 w-24" />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
