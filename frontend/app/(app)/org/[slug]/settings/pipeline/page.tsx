import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getStages, type StageDto } from "@/lib/api/crm";
import { StageConfigList } from "@/components/settings/StageConfigList";

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

  let stages: StageDto[] = [];
  try {
    stages = await getStages();
  } catch {
    /* non-fatal: render empty list */
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
      <StageConfigList slug={slug} stages={stages} />
    </div>
  );
}
