"use client";

import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { ExecutionGateCard } from "@/components/ai/execution-gate-card";
import { EmptyState } from "@/components/empty-state";
import type { CorrespondenceOrigin } from "@/lib/types";
import { approveGateAction, rejectGateAction } from "./actions";
import type { AiGateListItem } from "@/lib/api/ai";
import { ShieldCheck } from "lucide-react";

interface AiReviewsClientProps {
  slug: string;
  pendingGates: AiGateListItem[];
  historyGates: AiGateListItem[];
  /** gateId -> originating correspondence reference (CREATE_TASK_FROM_CORRESPONDENCE gates only). */
  correspondenceOrigins?: Record<string, CorrespondenceOrigin>;
}

export function AiReviewsClient({
  slug,
  pendingGates,
  historyGates,
  correspondenceOrigins = {},
}: AiReviewsClientProps) {
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
            variant="default"
            icon={ShieldCheck}
            title="No pending reviews"
            description="All AI-proposed actions have been reviewed. New proposals will appear here when AI skills request approval."
          />
        ) : (
          <div className="grid gap-4 sm:grid-cols-1 lg:grid-cols-2">
            {pendingGates.map((gate) => (
              <ExecutionGateCard
                key={gate.id}
                gate={gate}
                slug={slug}
                correspondenceOrigin={correspondenceOrigins[gate.id]}
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
            variant="default"
            icon={ShieldCheck}
            title="No review history"
            description="Previously reviewed AI actions will appear here once gates have been approved or rejected."
          />
        ) : (
          <div className="grid gap-4 sm:grid-cols-1 lg:grid-cols-2">
            {historyGates.map((gate) => (
              <ExecutionGateCard
                key={gate.id}
                gate={gate}
                slug={slug}
                correspondenceOrigin={correspondenceOrigins[gate.id]}
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
