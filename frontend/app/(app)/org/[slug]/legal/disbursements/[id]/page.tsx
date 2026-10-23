import { notFound } from "next/navigation";
import { handleApiError } from "@/lib/api";
import { isModuleEnabledServer } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getDisbursement, type DisbursementResponse } from "@/lib/api/legal-disbursements";
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

  // Capability lookup is a best-effort secondary check — the backend enforces
  // APPROVE_DISBURSEMENTS / WRITE_OFF_DISBURSEMENTS authoritatively. Default to
  // false on failure so we hide the approve/reject and write-off/undo UI rather
  // than crashing the page.
  let canApprove = false;
  let canWriteOff = false;
  try {
    const caps = await fetchMyCapabilities();
    canApprove =
      caps.isAdmin || caps.isOwner || caps.capabilities.includes("APPROVE_DISBURSEMENTS");
    canWriteOff =
      caps.isAdmin || caps.isOwner || caps.capabilities.includes("WRITE_OFF_DISBURSEMENTS");
  } catch (error) {
    console.error("Failed to fetch capabilities on disbursement detail:", error);
  }

  return (
    <DisbursementDetailClient
      slug={slug}
      disbursement={disbursement}
      canApprove={canApprove}
      canWriteOff={canWriteOff}
    />
  );
}
