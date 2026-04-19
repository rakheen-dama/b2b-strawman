import { notFound } from "next/navigation";
import { handleApiError } from "@/lib/api";
import { isModuleEnabledServer } from "@/lib/api/settings";
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

  return <DisbursementDetailClient slug={slug} disbursement={disbursement} />;
}
