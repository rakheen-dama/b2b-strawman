import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getAiGates } from "@/lib/api/ai";
import type { AiGateListItem } from "@/lib/api/ai";
import { AiReviewsClient } from "./reviews-client";

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

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">AI Reviews</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Review and approve AI-proposed actions before they are executed.
        </p>
      </div>

      <AiReviewsClient slug={slug} pendingGates={pendingGates} historyGates={historyGates} />
    </div>
  );
}
