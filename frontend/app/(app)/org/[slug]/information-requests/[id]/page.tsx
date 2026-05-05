import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { handleApiError } from "@/lib/api";
import { getRequest } from "@/lib/api/information-requests";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { RequestDetailClient } from "@/components/information-requests/request-detail-client";
import { SpecialistLauncherButton } from "@/components/assistant/specialist-launcher-button";
import { SPECIALIST_STRINGS } from "@/components/assistant/specialist-strings";

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
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Information Request
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
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
          href={`/org/${slug}/customers/${request.customerId}`}
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to {request.customerName}
        </Link>
      </div>

      <div className="flex items-center justify-end">
        <SpecialistLauncherButton
          specialistId="INTAKE"
          surface="INFO_REQUEST_REVIEW"
          contextRef={{ entityType: "informationRequest", entityId: request.id }}
          initialPrompt="Extract client-supplied fields from the uploaded documents."
          ctaLabel={SPECIALIST_STRINGS.intakeInfoRequestLabel}
        />
      </div>

      <RequestDetailClient request={request} slug={slug} />
    </div>
  );
}
