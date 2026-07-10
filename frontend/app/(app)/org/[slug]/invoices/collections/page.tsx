import { notFound } from "next/navigation";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getAiGates, getAiGate } from "@/lib/api/ai";
import type { AiGateListItem } from "@/lib/api/ai";
import { getDebtors } from "@/lib/api/collections";
import type { DebtorResponse } from "@/lib/api/collections";
import { CollectionsClient } from "./collections-client";
import type { ReminderPreview } from "./reminder-queue";

const REMINDER_GATE_TYPE = "SEND_COLLECTION_REMINDER";

function stringOrNull(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}

/**
 * Resolve the drafted subject/body preview for each pending SEND_COLLECTION_REMINDER gate.
 * The gate LIST DTO carries no proposedAction, so we fetch each gate's DETAIL and read the
 * snake_case content keys from its proposedAction (frontend-only — no backend change).
 * Best-effort: a failed detail fetch simply omits that gate's preview.
 */
async function resolveReminderPreviews(
  gates: AiGateListItem[]
): Promise<Record<string, ReminderPreview>> {
  const results = await Promise.allSettled(gates.map((g) => getAiGate(g.id)));
  const previews: Record<string, ReminderPreview> = {};
  results.forEach((result, idx) => {
    if (result.status !== "fulfilled") return;
    const action = result.value.proposedAction ?? {};
    previews[gates[idx].id] = {
      subject: stringOrNull(action["subject"]),
      bodyHtml: stringOrNull(action["body_html"]),
      bodyText: stringOrNull(action["body_text"]),
      stage: stringOrNull(action["stage"]),
      invoiceId: stringOrNull(action["invoice_id"]),
      customerId: stringOrNull(action["customer_id"]),
    };
  });
  return previews;
}

export default async function CollectionsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();

  // Page access mirrors the invoices list guard.
  if (!caps.isAdmin && !caps.isOwner && !caps.capabilities.includes("INVOICING")) {
    notFound();
  }

  // The actionable pending-reminder queue requires AI_REVIEW (admins/owners pass).
  const canReviewGates = caps.isAdmin || caps.isOwner || caps.capabilities.includes("AI_REVIEW");

  let debtors: DebtorResponse[] = [];
  try {
    const page = await getDebtors({ page: 0, size: 50 });
    debtors = page.content;
  } catch (error) {
    // Non-fatal: show the empty debtor book, but surface the failure in logs so a
    // failed fetch is not silently indistinguishable from "no outstanding balances".
    console.error("Failed to load debtors for collections page", error);
  }

  let gates: AiGateListItem[] | null = null;
  let previews: Record<string, ReminderPreview> = {};
  if (canReviewGates) {
    gates = [];
    try {
      const page = await getAiGates({
        gateType: REMINDER_GATE_TYPE,
        status: "PENDING",
        size: 50,
      });
      gates = page.content;
      previews = await resolveReminderPreviews(gates);
    } catch (error) {
      // Non-fatal: show the empty queue, but log so a failed fetch is not silently
      // indistinguishable from "no reminders awaiting approval".
      console.error("Failed to load reminder gates for collections page", error);
      gates = [];
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Collections</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Track outstanding debtors and approve drafted reminders before they are sent.
        </p>
      </div>

      <CollectionsClient slug={slug} debtors={debtors} gates={gates} previews={previews} />
    </div>
  );
}
