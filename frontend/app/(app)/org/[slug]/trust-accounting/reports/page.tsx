import Link from "next/link";
import { notFound } from "next/navigation";
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

// -- Page -----------------------------------------------------------------

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

  // Fetch report definitions and filter to TRUST category
  let trustReports: { slug: string; name: string; description: string }[] = [];
  let fetchError = false;
  try {
    const data = await getReportDefinitions();
    const trustCategory = data.categories.find((c) => c.category === "TRUST");
    trustReports = trustCategory?.reports ?? [];
  } catch {
    fetchError = true;
  }

  return (
    <div className="space-y-8" data-testid="trust-reports-page">
      {/* Header */}
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Trust Reports
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Generate and download trust accounting reports
        </p>
      </div>

      {/* Error State */}
      {fetchError && (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Unable to load report definitions. Please try again later.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Empty State */}
      {!fetchError && trustReports.length === 0 && (
        <Card>
          <CardContent className="py-10 text-center">
            <p className="text-sm text-slate-500 dark:text-slate-400">
              No trust reports available. Report definitions will appear here
              once configured.
            </p>
          </CardContent>
        </Card>
      )}

      {/* Report Cards Grid */}
      {trustReports.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {trustReports.map((report) => (
            <Link
              key={report.slug}
              href={`/org/${slug}/reports/${report.slug}`}
            >
              <Card
                className="h-full transition-shadow hover:shadow-md"
                data-testid={`report-card-${report.slug}`}
              >
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
