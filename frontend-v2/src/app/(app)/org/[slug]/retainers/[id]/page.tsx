import { fetchRetainer, fetchPeriods } from "@/lib/api/retainers";
import { handleApiError } from "@/lib/api";
import { RetainerDetailClient } from "@/components/retainers/retainer-detail-client";

interface RetainerDetailPageProps {
  params: Promise<{ slug: string; id: string }>;
}

export default async function RetainerDetailPage({
  params,
}: RetainerDetailPageProps) {
  const { slug, id } = await params;

  let retainer;
  let periodsData;
  try {
    [retainer, periodsData] = await Promise.all([
      fetchRetainer(id),
      fetchPeriods(id, 0),
    ]);
  } catch (error) {
    handleApiError(error);
  }

  return (
    <RetainerDetailClient
      retainer={retainer}
      periods={periodsData.content}
      orgSlug={slug}
    />
  );
}
