import Link from "next/link";
import { BarChart3 } from "lucide-react";
import { getReportDefinitions } from "@/lib/api/reports";
import type { ReportListResponse } from "@/lib/api/reports";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { EmptyState } from "@/components/empty-state";

export default async function ReportsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let data: ReportListResponse = { categories: [] };
  try {
    data = await getReportDefinitions();
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Reports
        </h1>
      </div>

      {/* Category Sections */}
      {data.categories.length === 0 ? (
        <EmptyState
          icon={BarChart3}
          title="No reports available"
          description="Report definitions will appear here once configured."
        />
      ) : (
        data.categories.map((category) => (
          <section key={category.category} className="space-y-4">
            <h2 className="font-display text-xl text-slate-900 dark:text-slate-100">
              {category.label}
            </h2>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {category.reports.map((report) => (
                <Link
                  key={report.slug}
                  href={`/org/${slug}/reports/${report.slug}`}
                >
                  <Card className="h-full transition-shadow hover:shadow-md">
                    <CardHeader>
                      <CardTitle className="text-slate-950 dark:text-slate-50">
                        {report.name}
                      </CardTitle>
                      <CardDescription>{report.description}</CardDescription>
                    </CardHeader>
                    <CardContent>
                      <span className="text-sm font-medium text-teal-600 dark:text-teal-400">
                        Run Report &rarr;
                      </span>
                    </CardContent>
                  </Card>
                </Link>
              ))}
            </div>
          </section>
        ))
      )}
    </div>
  );
}
