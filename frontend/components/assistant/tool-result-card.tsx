import { CheckCircle2, XCircle } from "lucide-react";

interface ToolResultCardProps {
  message: {
    toolName?: string;
    content: string;
    success?: boolean;
  };
}

export function ToolResultCard({ message }: ToolResultCardProps) {
  const isSuccess = message.success !== false;
  const isCancelled =
    message.content === "User cancelled this action" ||
    message.content.toLowerCase().includes("cancel");

  if (isCancelled || !isSuccess) {
    return (
      <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400">
        <XCircle className="size-3 shrink-0" />
        <span>Cancelled</span>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-700 dark:border-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-400">
      <CheckCircle2 className="size-3 shrink-0" />
      <span className="font-medium">
        {message.toolName
          ? message.toolName
              .split("_")
              .map((w: string) => w.charAt(0).toUpperCase() + w.slice(1))
              .join(" ")
          : "Done"}
      </span>
    </div>
  );
}
