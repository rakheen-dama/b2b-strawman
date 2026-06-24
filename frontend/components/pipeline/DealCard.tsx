"use client";

import { useDraggable } from "@dnd-kit/core";
import { CSS } from "@dnd-kit/utilities";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { formatCurrency } from "@/lib/format";
import type { DealResponse } from "@/lib/api/crm";

export interface DealCardProps {
  deal: DealResponse;
  customerName: string;
  ownerName: string | null;
  /** When true, the card is rendered statically (e.g. inside DragOverlay or a non-draggable column). */
  overlay?: boolean;
  /** When false, the card is not draggable (read-only users). Defaults to true. */
  draggable?: boolean;
}

export function DealCardView({ deal, customerName, ownerName }: DealCardProps) {
  const value =
    deal.valueAmount != null ? formatCurrency(deal.valueAmount, deal.valueCurrency) : "—";
  const weighted =
    deal.weightedValue != null ? formatCurrency(deal.weightedValue, deal.valueCurrency) : null;

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3 shadow-sm dark:border-slate-800 dark:bg-slate-950">
      <p className="truncate text-xs text-slate-500 dark:text-slate-400">{customerName}</p>
      <p className="mt-0.5 line-clamp-2 text-sm font-medium text-slate-950 dark:text-slate-50">
        {deal.title}
      </p>
      <div className="mt-2 flex items-center justify-between gap-2">
        <span className="text-sm font-semibold text-slate-900 dark:text-slate-100">{value}</span>
        <span className="text-xs text-slate-500 dark:text-slate-400">
          {deal.effectiveProbabilityPct}%{weighted ? ` · ${weighted}` : ""}
        </span>
      </div>
      {ownerName && (
        <div className="mt-2 flex items-center gap-1.5">
          <AvatarCircle name={ownerName} size={20} />
          <span className="truncate text-xs text-slate-500 dark:text-slate-400">{ownerName}</span>
        </div>
      )}
    </div>
  );
}

export function DealCard(props: DealCardProps) {
  const { deal, overlay, draggable = true } = props;
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: deal.id,
    disabled: !draggable,
  });

  if (overlay) {
    return (
      <div className="w-64 cursor-grabbing">
        <DealCardView {...props} />
      </div>
    );
  }

  // Read-only users: render a static (non-draggable) card with no drag affordances.
  if (!draggable) {
    return <DealCardView {...props} />;
  }

  const style: React.CSSProperties = {
    transform: CSS.Translate.toString(transform),
    opacity: isDragging ? 0.4 : 1,
    cursor: "grab",
  };

  return (
    <div ref={setNodeRef} style={style} {...listeners} {...attributes}>
      <DealCardView {...props} />
    </div>
  );
}
