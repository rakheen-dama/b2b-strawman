"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  KeyboardSensor,
  useSensor,
  useSensors,
  useDroppable,
  closestCorners,
  type DragStartEvent,
  type DragEndEvent,
} from "@dnd-kit/core";
import { sortableKeyboardCoordinates } from "@dnd-kit/sortable";
import { formatCurrency } from "@/lib/format";
import { DealCard } from "./DealCard";
import { WinLoseDialog } from "./WinLoseDialog";
import { transitionDealAction } from "@/app/(app)/org/[slug]/pipeline/actions";
import type { DealResponse, StageDto } from "@/lib/api/crm";

export interface PipelineBoardProps {
  slug: string;
  stages: StageDto[];
  deals: DealResponse[];
  customerNames: Record<string, string>;
  ownerNames: Record<string, string>;
  canManage: boolean;
}

function StageColumn({
  stage,
  deals,
  customerNames,
  ownerNames,
  collapsed,
}: {
  stage: StageDto;
  deals: DealResponse[];
  customerNames: Record<string, string>;
  ownerNames: Record<string, string>;
  collapsed?: boolean;
}) {
  const { setNodeRef, isOver } = useDroppable({ id: stage.id });
  const total = deals.reduce((sum, d) => sum + (d.valueAmount ?? 0), 0);
  const currency = deals[0]?.valueCurrency ?? "ZAR";

  return (
    <div
      ref={setNodeRef}
      className={`flex w-72 shrink-0 flex-col rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900/40 ${
        isOver ? "ring-2 ring-teal-500" : ""
      } ${collapsed ? "opacity-90" : ""}`}
    >
      <div className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{stage.name}</h3>
        <span className="rounded-full bg-slate-200 px-2 py-0.5 text-xs text-slate-700 dark:bg-slate-800 dark:text-slate-300">
          {deals.length}
        </span>
      </div>
      <p className="mb-3 text-xs text-slate-500 dark:text-slate-400">
        {formatCurrency(total, currency)}
      </p>
      <div className="flex flex-col gap-2">
        {deals.map((deal) => (
          <DealCard
            key={deal.id}
            deal={deal}
            customerName={customerNames[deal.customerId] ?? "Unknown customer"}
            ownerName={deal.ownerId ? (ownerNames[deal.ownerId] ?? null) : null}
          />
        ))}
        {deals.length === 0 && (
          <p className="rounded-md border border-dashed border-slate-300 px-3 py-6 text-center text-xs text-slate-400 dark:border-slate-700 dark:text-slate-500">
            No deals
          </p>
        )}
      </div>
    </div>
  );
}

export function PipelineBoard({
  slug,
  stages,
  deals,
  customerNames,
  ownerNames,
  canManage,
}: PipelineBoardProps) {
  const router = useRouter();
  const [activeDeal, setActiveDeal] = useState<DealResponse | null>(null);
  const [winLoseDeal, setWinLoseDeal] = useState<DealResponse | null>(null);
  const [winLoseStage, setWinLoseStage] = useState<StageDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const openStages = useMemo(
    () => stages.filter((s) => s.stageType === "OPEN" && !s.archived),
    [stages]
  );
  const closedStages = useMemo(
    () => stages.filter((s) => s.stageType !== "OPEN" && !s.archived),
    [stages]
  );
  const stageById = useMemo(() => new Map(stages.map((s) => [s.id, s])), [stages]);

  const dealsByStage = useMemo(() => {
    const map: Record<string, DealResponse[]> = {};
    for (const stage of stages) map[stage.id] = [];
    for (const deal of deals) {
      if (map[deal.stageId]) map[deal.stageId].push(deal);
    }
    return map;
  }, [stages, deals]);

  function handleDragStart(e: DragStartEvent) {
    if (!canManage) return;
    const id = e.active.id as string;
    setActiveDeal(deals.find((d) => d.id === id) ?? null);
  }

  async function handleDragEnd(e: DragEndEvent) {
    const { active, over } = e;
    const deal = activeDeal;
    setActiveDeal(null);
    if (!canManage || !over || !deal) return;

    const targetStageId = over.id as string;
    if (targetStageId === deal.stageId) return;

    const targetStage = stageById.get(targetStageId);
    if (!targetStage) return;

    if (targetStage.stageType === "WON" || targetStage.stageType === "LOST") {
      // Win/Lose requires confirmation (LOST needs a reason).
      setError(null);
      setWinLoseDeal(deal);
      setWinLoseStage(targetStage);
      return;
    }

    // OPEN → OPEN move.
    setError(null);
    try {
      const result = await transitionDealAction(slug, deal.id, { targetStageId });
      if (result.success) {
        router.refresh();
      } else {
        // The card snaps back on refresh; surface why the move was rejected.
        setError(result.error ?? "Could not move the deal. Please try again.");
      }
    } catch {
      setError("A network error occurred while moving the deal.");
    }
  }

  return (
    <>
      {error && (
        <p
          role="alert"
          className="text-destructive mb-3 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm dark:border-red-900/40 dark:bg-red-950/30"
        >
          {error}
        </p>
      )}
      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
      >
        <div className="flex gap-4 overflow-x-auto pb-4">
          {openStages.map((stage) => (
            <StageColumn
              key={stage.id}
              stage={stage}
              deals={dealsByStage[stage.id] ?? []}
              customerNames={customerNames}
              ownerNames={ownerNames}
            />
          ))}
          {closedStages.map((stage) => (
            <StageColumn
              key={stage.id}
              stage={stage}
              deals={dealsByStage[stage.id] ?? []}
              customerNames={customerNames}
              ownerNames={ownerNames}
              collapsed
            />
          ))}
        </div>

        <DragOverlay>
          {activeDeal ? (
            <DealCard
              deal={activeDeal}
              customerName={customerNames[activeDeal.customerId] ?? "Unknown customer"}
              ownerName={activeDeal.ownerId ? (ownerNames[activeDeal.ownerId] ?? null) : null}
              overlay
            />
          ) : null}
        </DragOverlay>
      </DndContext>

      <WinLoseDialog
        slug={slug}
        deal={winLoseDeal}
        targetStage={winLoseStage}
        open={winLoseDeal != null && winLoseStage != null}
        onOpenChange={(o) => {
          if (!o) {
            setWinLoseDeal(null);
            setWinLoseStage(null);
          }
        }}
      />
    </>
  );
}
