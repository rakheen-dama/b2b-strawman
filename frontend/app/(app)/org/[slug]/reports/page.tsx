import Link from "next/link";
import { BarChart3 } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getReportDefinitions } from "@/lib/api/reports";
import { PermissionDenied } from "@/components/permission-denied";
import type { ReportListResponse } from "@/lib/api/reports";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@b2mash/ui/card";
import { PageHeader } from "@b2mash/ui/page-header";
import { EmptyState } from "@/components/empty-state";

function ReportCard({
  href,
  name,
  description,
}: {
  href: string;
  name: string;
  description: string;
}) {
  return (
    <Link href={href}>
      <Card className="h-full transition-shadow hover:shadow-md">
        <CardHeader>
          <CardTitle>{name}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </CardHeader>
        <CardContent>
          <span className="text-sm font-medium text-teal-600 dark:text-teal-400">
            Run report &rarr;
          </span>
        </CardContent>
      </Card>
    </Link>
  );
}

export default async function ReportsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const capData = await fetchMyCapabilities();

  if (
    !capData.isAdmin &&
    !capData.isOwner &&
    !capData.capabilities.includes("FINANCIAL_VISIBILITY")
  ) {
    return <PermissionDenied featureName="Reports" dashboardHref={`/org/${slug}/dashboard`} />;
  }

  let data: ReportListResponse = { categories: [] };
  let fetchError = false;
  try {
    data = await getReportDefinitions();
  } catch (e) {
    console.error("Failed to fetch report definitions:", e);
    fetchError = true;
  }

  return (
    <div className="space-y-8">
      <PageHeader title="Reports" />

      {/* Profitability is a built-in page, not a backend report definition: this card is its single
          UI entry (Phase 87 S6a, ADR-339). Its page applies the same gate as this index (admin,
          owner or FINANCIAL_VISIBILITY), so anyone past the gate above may open it. */}
      <section className="space-y-4">
        <h2 className="font-display text-foreground text-xl">Firm performance</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <ReportCard
            href={`/org/${slug}/reports/profitability`}
            name="Profitability"
            description="Team utilization, matter profitability, and client profitability across your organization"
          />
        </div>
      </section>

      {/* Category Sections */}
      {data.categories.length === 0 ? (
        <EmptyState
          icon={BarChart3}
          title={fetchError ? "Unable to load reports" : "No reports available"}
          description={
            fetchError
              ? "There was a problem loading report definitions. Please try again later."
              : "Report definitions will appear here once configured."
          }
        />
      ) : (
        data.categories.map((category) => (
          <section key={category.category} className="space-y-4">
            <h2 className="font-display text-foreground text-xl">{category.label}</h2>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {category.reports.map((report) => (
                <ReportCard
                  key={report.slug}
                  href={`/org/${slug}/reports/${report.slug}`}
                  name={report.name}
                  description={report.description}
                />
              ))}
            </div>
          </section>
        ))
      )}
    </div>
  );
}
