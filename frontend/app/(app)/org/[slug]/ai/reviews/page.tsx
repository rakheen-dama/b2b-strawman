import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getAiGates, getAiGate } from "@/lib/api/ai";
import type { AiGateListItem } from "@/lib/api/ai";
import type { CorrespondenceOrigin } from "@/components/ai/execution-gate-card";
import { AiReviewsClient } from "./reviews-client";

const CORRESPONDENCE_GATE_TYPE = "CREATE_TASK_FROM_CORRESPONDENCE";

/**
 * Resolve the originating-correspondence reference for any CREATE_TASK_FROM_CORRESPONDENCE gates.
 * The list DTO carries no proposedAction, so we fetch each such gate's detail and read {@code
 * project_id}/{@code correspondence_id} from its proposedAction (frontend-only — no backend change,
 * no subject). Best-effort: a failed detail fetch simply omits that gate's link.
 */
async function resolveCorrespondenceOrigins(
  gates: AiGateListItem[]
): Promise<Record<string, CorrespondenceOrigin>> {
  const corrGates = gates.filter((g) => g.gateType === CORRESPONDENCE_GATE_TYPE);
  const results = await Promise.allSettled(corrGates.map((g) => getAiGate(g.id)));
  const origins: Record<string, CorrespondenceOrigin> = {};
  results.forEach((result, idx) => {
    if (result.status !== "fulfilled") return;
    const action = result.value.proposedAction ?? {};
    const projectId = action["project_id"];
    if (typeof projectId !== "string" || projectId.length === 0) return;
    const correspondenceId = action["correspondence_id"];
    origins[corrGates[idx].id] = {
      projectId,
      correspondenceId: typeof correspondenceId === "string" ? correspondenceId : undefined,
    };
  });
  return origins;
}

export default async function AiReviewsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();

  if (!caps.capabilities.includes("AI_REVIEW") && !caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-6">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">AI Reviews</h1>
          <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
            You do not have permission to review AI actions. Contact your administrator.
          </p>
        </div>
      </div>
    );
  }

  let pendingGates: AiGateListItem[] = [];
  let historyGates: AiGateListItem[] = [];

  const [pendingResult, historyResult] = await Promise.allSettled([
    getAiGates({ status: "PENDING", size: 50 }),
    getAiGates({ size: 50 }),
  ]);

  if (pendingResult.status === "fulfilled") {
    pendingGates = pendingResult.value.content;
  }
  if (historyResult.status === "fulfilled") {
    historyGates = historyResult.value.content.filter((g) => g.status !== "PENDING");
  }

  const correspondenceOrigins = await resolveCorrespondenceOrigins([
    ...pendingGates,
    ...historyGates,
  ]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">AI Reviews</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Review and approve AI-proposed actions before they are executed.
        </p>
      </div>

      <AiReviewsClient
        slug={slug}
        pendingGates={pendingGates}
        historyGates={historyGates}
        correspondenceOrigins={correspondenceOrigins}
      />
    </div>
  );
}
