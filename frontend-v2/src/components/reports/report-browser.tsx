import Link from "next/link";
import { BarChart3, ArrowRight } from "lucide-react";

import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { EmptyState } from "@/components/empty-state";
import type { ReportListResponse } from "@/lib/api/reports";

interface ReportBrowserProps {
  data: ReportListResponse;
  orgSlug: string;
  fetchError?: boolean;
}

export function ReportBrowser({
  data,
  orgSlug,
  fetchError,
}: ReportBrowserProps) {
  if (data.categories.length === 0) {
    return (
      <EmptyState
        icon={BarChart3}
        title={fetchError ? "Unable to load reports" : "No reports available"}
        description={
          fetchError
            ? "There was a problem loading report definitions. Please try again later."
            : "Report definitions will appear here once configured."
        }
      />
    );
  }

  return (
    <div className="space-y-8">
      {data.categories.map((category) => (
        <section key={category.category} className="space-y-4">
          <h2 className="font-display text-lg font-semibold text-slate-900">
            {category.label}
          </h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {category.reports.map((report) => (
              <Link
                key={report.slug}
                href={`/org/${orgSlug}/reports/${report.slug}`}
              >
                <Card className="group h-full transition-shadow hover:shadow-md">
                  <CardHeader>
                    <CardTitle className="text-slate-950">
                      {report.name}
                    </CardTitle>
                    <CardDescription>{report.description}</CardDescription>
                  </CardHeader>
                  <CardContent>
                    <span className="inline-flex items-center gap-1 text-sm font-medium text-teal-600 transition-colors group-hover:text-teal-700">
                      Run Report
                      <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" />
                    </span>
                  </CardContent>
                </Card>
              </Link>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
