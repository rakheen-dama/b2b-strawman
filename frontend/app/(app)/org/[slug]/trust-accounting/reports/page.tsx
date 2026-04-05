import { notFound } from "next/navigation";
import Link from "next/link";
import { FileBarChart } from "lucide-react";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getReportDefinitions } from "@/lib/api/reports";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { EmptyState } from "@/components/empty-state";
import type { ReportListItem } from "@/lib/api/reports";

// ── Page ───────────────────────────────────────────────────────────

export default async function TrustReportsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  // Module gating
  let settings;
  try {
    settings = await getOrgSettings();
  } catch {
    notFound();
  }

  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("trust_accounting")) {
    notFound();
  }

  // Capability check
  const capData = await fetchMyCapabilities();
  const hasViewTrust =
    capData.isAdmin ||
    capData.isOwner ||
    capData.capabilities.includes("VIEW_TRUST");
  if (!hasViewTrust) {
    notFound();
  }

  // Fetch report definitions and filter for TRUST category
  let trustReports: ReportListItem[] = [];
  let fetchError = false;
  try {
    const data = await getReportDefinitions();
    const trustCategory = data.categories.find(
      (c) => c.category === "TRUST",
    );
    if (trustCategory) {
      trustReports = trustCategory.reports;
    }
  } catch (e) {
    console.error("Failed to fetch report definitions:", e);
    fetchError = true;
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Trust Reports
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          LSSA-compliant trust accounting reports
        </p>
      </div>

      {trustReports.length === 0 ? (
        <EmptyState
          icon={FileBarChart}
          title={
            fetchError
              ? "Unable to load reports"
              : "No trust reports available"
          }
          description={
            fetchError
              ? "There was a problem loading report definitions. Please try again later."
              : "Trust report definitions will appear here once configured."
          }
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {trustReports.map((report) => (
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
      )}
    </div>
  );
}
