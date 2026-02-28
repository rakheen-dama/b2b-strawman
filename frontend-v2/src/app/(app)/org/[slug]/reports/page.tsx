import { getReportDefinitions } from "@/lib/api/reports";
import type { ReportListResponse } from "@/lib/api/reports";
import { PageHeader } from "@/components/layout/page-header";
import { ReportBrowser } from "@/components/reports/report-browser";

export default async function ReportsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let data: ReportListResponse = { categories: [] };
  let fetchError = false;
  try {
    data = await getReportDefinitions();
  } catch (e) {
    console.error("Failed to fetch report definitions:", e);
    fetchError = true;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Reports"
        description="Pre-built reports for your organization"
      />

      <ReportBrowser data={data} orgSlug={slug} fetchError={fetchError} />
    </div>
  );
}
