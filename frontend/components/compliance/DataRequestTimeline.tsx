import { cn } from "@/lib/utils";
import type { DataRequestResponse } from "@/lib/types";

interface DataRequestTimelineProps {
  request: DataRequestResponse;
}

function isOverdue(deadline: string): boolean {
  const today = new Date().toLocaleDateString("en-CA"); // "YYYY-MM-DD" local date
  return deadline < today;
}

function formatDate(isoString: string): string {
  return new Date(isoString).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

function formatLocalDate(yyyyMmDd: string): string {
  const [year, month, day] = yyyyMmDd.split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString("en-ZA", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

interface TimelineStageProps {
  label: string;
  date?: string | null;
  isComplete: boolean;
  isActive?: boolean;
  children?: React.ReactNode;
}

function TimelineStage({ label, date, isComplete, isActive, children }: TimelineStageProps) {
  return (
    <div className="flex gap-4">
      {/* Dot + line */}
      <div className="flex flex-col items-center">
        <div
          className={cn(
            "size-3 rounded-full ring-2 ring-offset-2 ring-offset-white dark:ring-offset-slate-950 mt-0.5",
            isComplete
              ? "bg-teal-600 ring-teal-600"
              : isActive
                ? "bg-slate-400 ring-slate-400"
                : "bg-slate-200 ring-slate-200 dark:bg-slate-700 dark:ring-slate-700",
          )}
        />
        <div className="mt-1 flex-1 w-px bg-slate-200 dark:bg-slate-800 min-h-8" />
      </div>

      {/* Content */}
      <div className="pb-6">
        <p
          className={cn(
            "text-sm font-medium",
            isComplete || isActive
              ? "text-slate-900 dark:text-slate-100"
              : "text-slate-400 dark:text-slate-600",
          )}
        >
          {label}
        </p>
        {date && (
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">{date}</p>
        )}
        {children}
      </div>
    </div>
  );
}

export function DataRequestTimeline({ request }: DataRequestTimelineProps) {
  const statusOrder = ["RECEIVED", "IN_PROGRESS", "COMPLETED", "REJECTED"];
  const currentIndex = statusOrder.indexOf(request.status);
  const isCompleted = request.status === "COMPLETED";
  const isRejected = request.status === "REJECTED";
  const isInProgress = request.status === "IN_PROGRESS";
  const overdue = isOverdue(request.deadline);

  return (
    <div className="space-y-0">
      {/* RECEIVED */}
      <TimelineStage
        label="Request Received"
        date={formatDate(request.requestedAt)}
        isComplete={currentIndex >= 0}
        isActive={request.status === "RECEIVED"}
      />

      {/* IN_PROGRESS */}
      <TimelineStage
        label="Processing Started"
        date={isInProgress || isCompleted || isRejected ? undefined : undefined}
        isComplete={isCompleted || isRejected}
        isActive={isInProgress}
      />

      {/* COMPLETED or REJECTED */}
      {isCompleted && (
        <TimelineStage
          label="Request Completed"
          date={request.completedAt ? formatDate(request.completedAt) : undefined}
          isComplete={true}
        />
      )}
      {isRejected && (
        <TimelineStage
          label="Request Rejected"
          date={request.completedAt ? formatDate(request.completedAt) : undefined}
          isComplete={true}
        >
          {request.rejectionReason && (
            <p className="mt-1 text-xs text-slate-600 dark:text-slate-400">
              Reason: {request.rejectionReason}
            </p>
          )}
        </TimelineStage>
      )}
      {!isCompleted && !isRejected && (
        <TimelineStage
          label="Awaiting Completion"
          isComplete={false}
          isActive={false}
        />
      )}

      {/* Deadline indicator */}
      <div className="flex gap-4 mt-2">
        <div className="flex flex-col items-center">
          <div
            className={cn(
              "size-3 rounded-full ring-2 ring-offset-2 ring-offset-white dark:ring-offset-slate-950 mt-0.5",
              overdue && !isCompleted
                ? "bg-red-500 ring-red-500"
                : "bg-slate-200 ring-slate-200 dark:bg-slate-700 dark:ring-slate-700",
            )}
          />
        </div>
        <div>
          <p
            className={cn(
              "text-sm font-medium",
              overdue && !isCompleted
                ? "text-red-600 dark:text-red-400"
                : "text-slate-500 dark:text-slate-400",
            )}
          >
            Deadline: {formatLocalDate(request.deadline)}
            {overdue && !isCompleted && " (Overdue)"}
          </p>
        </div>
      </div>
    </div>
  );
}
