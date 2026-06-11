"use client";

import { useState, useCallback } from "react";
import useSWR from "swr";
import { Check, X, Loader2 } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { Badge } from "@b2mash/ui/badge";
import { AI_QUEUE_STRINGS } from "@/lib/constants/ai-queue-strings";
import {
  approveInvocation,
  rejectInvocation,
  listInvocationsClient,
  type InvocationListItemClient,
  type InvocationPageClient,
} from "@/lib/api/assistant-specialists";

export interface PendingSuggestionsWidgetProps {
  contextEntityType: string;
  contextEntityId: string;
}

export function PendingSuggestionsWidget({
  contextEntityType,
  contextEntityId,
}: PendingSuggestionsWidgetProps) {
  const [actionInFlight, setActionInFlight] = useState<string | null>(null);

  const swrKey = `pending-suggestions-${contextEntityType}-${contextEntityId}`;
  const { data, isLoading, mutate } = useSWR<InvocationPageClient>(
    swrKey,
    () =>
      listInvocationsClient({
        contextEntityType,
        contextEntityId,
        status: "PENDING_APPROVAL",
        size: "10",
      }),
    { refreshInterval: 30000 }
  );

  const invocations: InvocationListItemClient[] = data?.content ?? [];

  const handleApprove = useCallback(
    async (id: string) => {
      setActionInFlight(id);
      try {
        await approveInvocation(id);
        await mutate();
      } catch {
        // Keep in list on failure
      } finally {
        setActionInFlight(null);
      }
    },
    [mutate]
  );

  const handleReject = useCallback(
    async (id: string) => {
      setActionInFlight(id);
      try {
        await rejectInvocation(id, "Rejected from entity detail page");
        await mutate();
      } catch {
        // Keep in list on failure
      } finally {
        setActionInFlight(null);
      }
    },
    [mutate]
  );

  if (isLoading) return null;
  if (invocations.length === 0) return null;

  return (
    <Card data-testid="pending-suggestions-widget">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium">
          {AI_QUEUE_STRINGS.pendingWidget.title}
          <Badge variant="warning" className="ml-2">
            {invocations.length}
          </Badge>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {invocations.map((inv) => (
          <div
            key={inv.id}
            className="flex items-center justify-between rounded-md border p-2 text-sm"
          >
            <div className="min-w-0 flex-1">
              <span className="font-medium">
                {AI_QUEUE_STRINGS.specialist[inv.specialistId] ?? inv.specialistId}
              </span>
              <span className="ml-2 text-slate-500">
                {inv.proposedOutputSummary ?? "Pending review"}
              </span>
            </div>
            <div className="flex items-center gap-1">
              {actionInFlight === inv.id ? (
                <Loader2 className="size-4 animate-spin text-slate-400" />
              ) : (
                <>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-7"
                    onClick={() => handleApprove(inv.id)}
                    aria-label="Approve"
                  >
                    <Check className="size-4 text-teal-600" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="size-7"
                    onClick={() => handleReject(inv.id)}
                    aria-label="Reject"
                  >
                    <X className="size-4 text-red-600" />
                  </Button>
                </>
              )}
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
