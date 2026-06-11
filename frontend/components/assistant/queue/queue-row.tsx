"use client";

import { Badge } from "@b2mash/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { TableCell, TableRow } from "@/components/ui/table";
import { AI_QUEUE_STRINGS } from "@/lib/constants/ai-queue-strings";
import { cn } from "@/lib/utils";

export interface QueueRowProps {
  id: string;
  specialistId: string;
  invokedBy: string;
  status: string;
  contextEntityType: string;
  contextEntityId: string;
  createdAt: string;
  proposedOutputSummary: string | null;
  selected: boolean;
  onSelect: (id: string, checked: boolean) => void;
  onClick: (id: string) => void;
}

const STATUS_VARIANT: Record<
  string,
  "default" | "success" | "warning" | "destructive" | "outline"
> = {
  RUNNING: "default",
  PENDING_APPROVAL: "warning",
  APPROVED: "success",
  REJECTED: "destructive",
  AUTO_APPLIED: "success",
  FAILED: "destructive",
  EXPIRED: "outline",
};

export function QueueRow({
  id,
  specialistId,
  invokedBy,
  status,
  contextEntityType,
  contextEntityId,
  createdAt,
  proposedOutputSummary,
  selected,
  onSelect,
  onClick,
}: QueueRowProps) {
  const statusLabel = AI_QUEUE_STRINGS.status[status] ?? status;
  const specialistLabel = AI_QUEUE_STRINGS.specialist[specialistId] ?? specialistId;
  const variant = STATUS_VARIANT[status] ?? "outline";

  return (
    <TableRow
      className={cn("cursor-pointer", selected && "bg-slate-50 dark:bg-slate-800/50")}
      onClick={() => onClick(id)}
      data-testid={`queue-row-${id}`}
    >
      <TableCell className="w-10" onClick={(e) => e.stopPropagation()}>
        <Checkbox
          checked={selected}
          onCheckedChange={(checked) => onSelect(id, !!checked)}
          aria-label={`Select invocation ${id}`}
        />
      </TableCell>
      <TableCell>
        <Badge variant={variant}>{statusLabel}</Badge>
      </TableCell>
      <TableCell className="font-medium">{specialistLabel}</TableCell>
      <TableCell className="capitalize">{contextEntityType}</TableCell>
      <TableCell className="text-sm text-slate-500">{contextEntityId.slice(0, 8)}...</TableCell>
      <TableCell className="text-sm text-slate-500 capitalize">{invokedBy.toLowerCase()}</TableCell>
      <TableCell className="text-sm text-slate-500">
        {new Date(createdAt).toLocaleString()}
      </TableCell>
      <TableCell className="text-sm text-slate-500">{proposedOutputSummary ?? "—"}</TableCell>
    </TableRow>
  );
}
