import { getReportDefinition } from "@/lib/api/reports";
import { handleApiError } from "@/lib/api";
import { PageHeader } from "@/components/layout/page-header";
import { ReportRunner } from "@/components/reports/report-runner";
import type { ReportDefinitionDetail } from "@/lib/api/reports";

export default async function ReportDetailPage({
  params,
}: {
  params: Promise<{ slug: string; reportSlug: string }>;
}) {
  const { slug, reportSlug } = await params;

  let definition: ReportDefinitionDetail;
  try {
    definition = await getReportDefinition(reportSlug);
  } catch (error) {
    handleApiError(error);
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={definition!.name}
        description={definition!.description}
        backHref={`/org/${slug}/reports`}
      />

      <ReportRunner definition={definition!} />
    </div>
  );
}
