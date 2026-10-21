"use client";

import { createContext, useContext } from "react";
import { MoreHorizontal } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { DiaryEventResponse } from "@/app/(app)/org/[slug]/diary/diary-types";

export type DiaryRowAction = "postpone" | "outcome" | "cancel" | "mark-done";

export const DIARY_ROW_ACTION_LABELS: Record<DiaryRowAction, string> = {
  postpone: "Postpone",
  outcome: "Record outcome",
  cancel: "Cancel",
  "mark-done": "Mark done",
};

export interface DiaryActionPermissions {
  /** Court-date / prescription writes need MANAGE_LEGAL (or admin / owner). */
  canManageLegal: boolean;
  /** Known admin / owner (fail-closed): may Mark done any fee earner's task. */
  canCompleteAnyTask: boolean;
  currentMemberId: string | null;
}

export interface DiaryActionsContextValue extends DiaryActionPermissions {
  onAction: (event: DiaryEventResponse, action: DiaryRowAction) => void;
}

const DiaryActionsContext = createContext<DiaryActionsContextValue | null>(null);

/**
 * Supplies row actions to every DiaryRowActions below it, so the views stay free of server-action
 * imports and take no extra props. Without a provider, rows render no actions.
 */
export const DiaryActionsProvider = DiaryActionsContext.Provider;

/**
 * Actions a diary row offers, mirroring the backend's transitions:
 * - court date (MANAGE_LEGAL): SCHEDULED → postpone / record outcome / cancel; POSTPONED → record
 *   outcome / cancel (a postponed date cannot be postponed again);
 * - task: mark done only from IN_PROGRESS (OPEN → DONE is not a valid transition), and only for
 *   the fee earner it is assigned to or an admin / owner (TaskService.completeTask);
 * - prescriptions and matter due dates: none (they are managed on the matter).
 */
export function availableDiaryActions(
  event: DiaryEventResponse,
  permissions: DiaryActionPermissions
): DiaryRowAction[] {
  if (event.kind === "COURT_DATE" && permissions.canManageLegal) {
    if (event.status === "SCHEDULED") return ["postpone", "outcome", "cancel"];
    if (event.status === "POSTPONED") return ["outcome", "cancel"];
  }
  if (event.kind === "TASK_DUE" && event.status === "IN_PROGRESS") {
    const isOwnTask =
      permissions.currentMemberId !== null &&
      (event.feeEarnerId ?? null) === permissions.currentMemberId;
    if (permissions.canCompleteAnyTask || isOwnTask) return ["mark-done"];
  }
  return [];
}

/** The "…" menu beside a diary row; renders nothing when the row has no available action. */
export function DiaryRowActions({ event }: { event: DiaryEventResponse }) {
  const ctx = useContext(DiaryActionsContext);
  if (!ctx) return null;
  const actions = availableDiaryActions(event, ctx);
  if (actions.length === 0) return null;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon-xs"
          aria-label={`Actions for ${event.title}`}
          data-testid={`diary-row-actions-${event.id}`}
          className="mt-1 shrink-0 text-slate-500 hover:text-slate-900 motion-reduce:transition-none dark:text-slate-400 dark:hover:text-slate-100"
        >
          <MoreHorizontal aria-hidden="true" className="size-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="end"
        // `!`: the base content's `data-[state=open]:animate-in` / `data-[state=closed]:animate-out`
        // are (0,2,0) selectors and would beat a plain (0,1,0) `motion-reduce:animate-none`.
        className="motion-reduce:animate-none!"
        data-testid={`diary-row-actions-menu-${event.id}`}
      >
        {actions.map((action) => (
          <DropdownMenuItem key={action} onSelect={() => ctx.onAction(event, action)}>
            {DIARY_ROW_ACTION_LABELS[action]}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
