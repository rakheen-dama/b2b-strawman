import { fetchRetainers } from "@/lib/api/retainers";
import { handleApiError } from "@/lib/api";
import { PageHeader } from "@/components/layout/page-header";
import { RetainerList } from "@/components/retainers/retainer-list";

interface RetainersPageProps {
  params: Promise<{ slug: string }>;
}

export default async function RetainersPage({ params }: RetainersPageProps) {
  const { slug } = await params;

  let retainers;
  try {
    retainers = await fetchRetainers();
  } catch (error) {
    handleApiError(error);
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Retainers"
        count={retainers.length}
        description="Recurring client agreements and hour banks"
      />

      <RetainerList retainers={retainers} orgSlug={slug} />
    </div>
  );
}
