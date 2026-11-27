import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { handleApiError } from "@/lib/api";
import { getRequest } from "@/lib/api/information-requests";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { RequestDetailClient } from "@/components/information-requests/request-detail-client";
import { PageHeader } from "@b2mash/ui/page-header";

export default async function InformationRequestDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const caps = await fetchMyCapabilities();

  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <PageHeader title="Information Request" />
        <p className="text-muted-foreground">
          You do not have permission to view information requests.
        </p>
      </div>
    );
  }

  let request;
  try {
    request = await getRequest(id);
  } catch (error) {
    handleApiError(error);
    return null;
  }

  return (
    <div className="space-y-8">
      <div>
        <Link
          href={`/org/${slug}/clients/${request.clientId}`}
          className="text-muted-foreground hover:text-foreground inline-flex items-center text-sm transition-colors"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to {request.clientName}
        </Link>
      </div>

      <RequestDetailClient request={request} slug={slug} />
    </div>
  );
}
