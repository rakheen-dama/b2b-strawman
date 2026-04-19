import { notFound } from "next/navigation";
import { handleApiError } from "@/lib/api";
import { isModuleEnabledServer } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import {
  getDisbursement,
  type DisbursementResponse,
} from "@/lib/api/legal-disbursements";
import { DisbursementDetailClient } from "./detail-client";

export default async function DisbursementDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;

  if (!(await isModuleEnabledServer("disbursements"))) {
    notFound();
  }

  let disbursement: DisbursementResponse;
  try {
    disbursement = await getDisbursement(id);
  } catch (error) {
    handleApiError(error);
  }

  const caps = await fetchMyCapabilities();
  const canApprove =
    caps.isAdmin || caps.isOwner || caps.capabilities.includes("APPROVE_DISBURSEMENTS");

  return (
    <DisbursementDetailClient
      slug={slug}
      disbursement={disbursement}
      canApprove={canApprove}
    />
  );
}
