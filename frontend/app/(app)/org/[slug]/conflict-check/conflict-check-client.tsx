"use client";

import { useState, useCallback, useTransition } from "react";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { ConflictCheckForm } from "@/components/legal/conflict-check-form";
import { ConflictCheckHistory } from "@/components/legal/conflict-check-history";
import { fetchConflictChecks } from "./actions";
import type { ConflictCheck } from "@/lib/types";

type ViewMode = "check" | "history";

interface ConflictCheckClientProps {
  initialChecks: ConflictCheck[];
  initialTotal: number;
  slug: string;
}

export function ConflictCheckClient({
  initialChecks,
  initialTotal,
  slug,
}: ConflictCheckClientProps) {
  const [view, setView] = useState<ViewMode>("check");
  const [checks, setChecks] = useState(initialChecks);
  const [total, setTotal] = useState(initialTotal);
  const [isPending, startTransition] = useTransition();

  const refetchHistory = useCallback(() => {
    startTransition(async () => {
      try {
        const res = await fetchConflictChecks();
        setChecks(res?.content ?? []);
        setTotal(res?.page?.totalElements ?? 0);
      } catch (err) {
        console.error("Failed to refetch conflict checks:", err);
      }
    });
  }, []);

  return (
    <div className="space-y-4">
      <Tabs
        value={view}
        onValueChange={(v) => setView(v as ViewMode)}
      >
        <TabsList>
          <TabsTrigger value="check">Run Check</TabsTrigger>
          <TabsTrigger value="history">
            History ({total})
          </TabsTrigger>
        </TabsList>
      </Tabs>

      <div
        className={cn(isPending && "opacity-50 transition-opacity")}
      >
        {view === "check" && (
          <ConflictCheckForm slug={slug} onCheckComplete={refetchHistory} />
        )}
        {view === "history" && (
          <ConflictCheckHistory
            initialChecks={checks}
            initialTotal={total}
            slug={slug}
          />
        )}
      </div>
    </div>
  );
}
