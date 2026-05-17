"use client";

import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { ExecutionGateCard } from "@/components/ai/execution-gate-card";
import { approveGateAction, rejectGateAction } from "./actions";
import type { AiGateListItem } from "@/lib/api/ai";
import { ShieldCheck } from "lucide-react";

interface AiReviewsClientProps {
  slug: string;
  pendingGates: AiGateListItem[];
  historyGates: AiGateListItem[];
}

export function AiReviewsClient({ slug, pendingGates, historyGates }: AiReviewsClientProps) {
  async function handleApprove(gateId: string, notes?: string) {
    return approveGateAction(slug, gateId, notes);
  }

  async function handleReject(gateId: string, notes?: string) {
    return rejectGateAction(slug, gateId, notes);
  }

  return (
    <Tabs defaultValue="pending">
      <TabsList>
        <TabsTrigger value="pending">
          Pending Review{pendingGates.length > 0 && ` (${pendingGates.length})`}
        </TabsTrigger>
        <TabsTrigger value="history">History</TabsTrigger>
      </TabsList>

      <TabsContent value="pending" className="mt-4">
        {pendingGates.length === 0 ? (
          <EmptyState
            title="No pending reviews"
            description="All AI-proposed actions have been reviewed. New proposals will appear here when AI skills request approval."
          />
        ) : (
          <div className="grid gap-4 sm:grid-cols-1 lg:grid-cols-2">
            {pendingGates.map((gate) => (
              <ExecutionGateCard
                key={gate.id}
                gate={gate}
                onApprove={handleApprove}
                onReject={handleReject}
              />
            ))}
          </div>
        )}
      </TabsContent>

      <TabsContent value="history" className="mt-4">
        {historyGates.length === 0 ? (
          <EmptyState
            title="No review history"
            description="Previously reviewed AI actions will appear here once gates have been approved or rejected."
          />
        ) : (
          <div className="grid gap-4 sm:grid-cols-1 lg:grid-cols-2">
            {historyGates.map((gate) => (
              <ExecutionGateCard
                key={gate.id}
                gate={gate}
                onApprove={handleApprove}
                onReject={handleReject}
              />
            ))}
          </div>
        )}
      </TabsContent>
    </Tabs>
  );
}

function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-slate-200 px-6 py-12 text-center dark:border-slate-800">
      <ShieldCheck className="mb-3 size-10 text-slate-300 dark:text-slate-600" />
      <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">{title}</h3>
      <p className="mt-1 max-w-sm text-sm text-slate-500 dark:text-slate-400">{description}</p>
    </div>
  );
}
