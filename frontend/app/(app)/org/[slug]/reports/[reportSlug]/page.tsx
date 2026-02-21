import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { getReportDefinition } from "@/lib/api/reports";
import { handleApiError } from "@/lib/api";
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
    <div className="space-y-8">
      <div>
        <Link
          href={`/org/${slug}/reports`}
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Reports
        </Link>
      </div>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          {definition!.name}
        </h1>
        {definition!.description && (
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            {definition!.description}
          </p>
        )}
      </div>

      <ReportRunner definition={definition!} orgSlug={slug} />
    </div>
  );
}
