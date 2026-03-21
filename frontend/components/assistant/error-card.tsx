import { AlertCircle } from "lucide-react";
import { cn } from "@/lib/utils";

interface ErrorCardProps {
  message: {
    content: string;
  };
}

export function ErrorCard({ message }: ErrorCardProps) {
  return (
    <div
      className={cn(
        "flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs",
        "text-red-700 dark:border-red-800 dark:bg-red-900/30 dark:text-red-400",
      )}
    >
      <AlertCircle className="mt-0.5 size-3 shrink-0" />
      <p>{message.content}</p>
    </div>
  );
}
