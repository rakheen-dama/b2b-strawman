"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface ConfirmationCardProps {
  message: {
    toolCallId?: string;
    toolName?: string;
    content: string;
  };
  onConfirm: (toolCallId: string, approved: boolean) => Promise<void>;
}

export function ConfirmationCard({
  message,
  onConfirm,
}: ConfirmationCardProps) {
  const [isPending, setIsPending] = useState(false);

  const toolCallId = message.toolCallId ?? "";
  const toolName = message.toolName ?? "action";

  let inputData: Record<string, unknown> = {};
  try {
    inputData = JSON.parse(message.content);
  } catch {
    // ignore
  }

  const actionTitle = toolName
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");

  const handleConfirm = async () => {
    setIsPending(true);
    await onConfirm(toolCallId, true);
  };

  const handleCancel = async () => {
    setIsPending(true);
    await onConfirm(toolCallId, false);
  };

  return (
    <div
      className={cn(
        "rounded-lg border-l-4 border-teal-600 bg-white p-4 shadow-sm",
        "dark:bg-slate-800",
      )}
    >
      <p className="mb-3 text-sm font-semibold text-slate-900 dark:text-slate-100">
        {actionTitle}
      </p>

      {Object.keys(inputData).length > 0 && (
        <dl className="mb-4 space-y-1">
          {Object.entries(inputData).map(([key, value]) => (
            <div key={key} className="flex gap-2 text-xs">
              <dt className="min-w-[80px] font-medium text-slate-500 dark:text-slate-400">
                {key}
              </dt>
              <dd className="text-slate-700 dark:text-slate-300">
                {String(value ?? "")}
              </dd>
            </div>
          ))}
        </dl>
      )}

      <div className="flex gap-2">
        <Button
          size="sm"
          variant="accent"
          onClick={handleConfirm}
          disabled={isPending}
        >
          Confirm
        </Button>
        <Button
          size="sm"
          variant="ghost"
          onClick={handleCancel}
          disabled={isPending}
        >
          Cancel
        </Button>
      </div>
    </div>
  );
}
