"use client";

import {
  DndContext,
  closestCenter,
  PointerSensor,
  KeyboardSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  useSortable,
  arrayMove,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical } from "lucide-react";
import type { StageDto } from "@/lib/api/crm";

export interface StageReorderProps {
  stages: StageDto[];
  onReorder: (orderedIds: string[]) => void;
  renderActions: (stage: StageDto) => React.ReactNode;
}

function SortableRow({
  stage,
  renderActions,
}: {
  stage: StageDto;
  renderActions: (stage: StageDto) => React.ReactNode;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: stage.id,
  });
  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.6 : 1,
  };

  const typeBadge: Record<StageDto["stageType"], string> = {
    OPEN: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
    WON: "bg-teal-100 text-teal-700 dark:bg-teal-900/50 dark:text-teal-300",
    LOST: "bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-300",
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-3 dark:border-slate-800 dark:bg-slate-950"
    >
      <button
        type="button"
        className="cursor-grab text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
        aria-label={`Reorder ${stage.name}`}
        {...attributes}
        {...listeners}
      >
        <GripVertical className="size-4" />
      </button>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="truncate font-medium text-slate-950 dark:text-slate-50">
            {stage.name}
          </span>
          <span className={`rounded-full px-2 py-0.5 text-xs ${typeBadge[stage.stageType]}`}>
            {stage.stageType}
          </span>
          {stage.archived && (
            <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700 dark:bg-amber-900/40 dark:text-amber-300">
              Archived
            </span>
          )}
        </div>
        <p className="text-xs text-slate-500 dark:text-slate-400">
          {stage.defaultProbabilityPct}% default probability
        </p>
      </div>
      <div className="flex shrink-0 items-center gap-1">{renderActions(stage)}</div>
    </div>
  );
}

export function StageReorder({ stages, onReorder, renderActions }: StageReorderProps) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  function handleDragEnd(e: DragEndEvent) {
    const { active, over } = e;
    if (!over || active.id === over.id) return;
    const oldIndex = stages.findIndex((s) => s.id === active.id);
    const newIndex = stages.findIndex((s) => s.id === over.id);
    if (oldIndex === -1 || newIndex === -1) return;
    const reordered = arrayMove(stages, oldIndex, newIndex);
    onReorder(reordered.map((s) => s.id));
  }

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <SortableContext items={stages.map((s) => s.id)} strategy={verticalListSortingStrategy}>
        <div className="space-y-2">
          {stages.map((stage) => (
            <SortableRow key={stage.id} stage={stage} renderActions={renderActions} />
          ))}
        </div>
      </SortableContext>
    </DndContext>
  );
}
