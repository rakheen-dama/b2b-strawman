import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft, TriangleAlert } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getStages, type StageDto } from "@/lib/api/crm";
import { StageConfigList } from "@/components/settings/StageConfigList";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

export default async function PipelineSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const capData = await fetchMyCapabilities();
  if (!capData.isAdmin && !capData.isOwner && !capData.capabilities.includes("MANAGE_PIPELINE")) {
    notFound();
  }

  // Distinguish a genuinely empty pipeline from a failed fetch: on error we show
  // an error alert (not the "no stages configured" empty state), so admins don't
  // mistake a transient outage for an unconfigured pipeline.
  let stages: StageDto[] = [];
  let loadError = false;
  try {
    stages = await getStages();
  } catch {
    loadError = true;
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" /> Settings
      </Link>
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Pipeline</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Configure pipeline stages, ordering, and probabilities.
        </p>
      </div>
      {loadError ? (
        <Alert variant="destructive">
          <TriangleAlert className="size-4" />
          <AlertTitle>Couldn&rsquo;t load stages</AlertTitle>
          <AlertDescription>
            We couldn&rsquo;t load your pipeline stages. This may be a temporary issue — please
            refresh the page to try again.
          </AlertDescription>
        </Alert>
      ) : (
        <StageConfigList slug={slug} stages={stages} />
      )}
    </div>
  );
}
