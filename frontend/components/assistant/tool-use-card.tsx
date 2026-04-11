"use client";

import { useState } from "react";
import { Loader2, ChevronDown, ChevronUp } from "lucide-react";
import { Collapsible, CollapsibleTrigger, CollapsibleContent } from "@/components/ui/collapsible";

interface ToolUseCardProps {
  message: {
    toolName?: string;
    content: string;
  };
  isLoading?: boolean;
}

export function ToolUseCard({ message, isLoading = false }: ToolUseCardProps) {
  const [isOpen, setIsOpen] = useState(false);
  const toolName = message.toolName ?? "tool";

  let inputData: Record<string, unknown> = {};
  try {
    inputData = JSON.parse(message.content);
  } catch {
    // ignore parse errors
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-2 text-xs dark:border-slate-700 dark:bg-slate-800/50">
      <div className="flex items-center gap-2">
        {isLoading ? (
          <>
            <Loader2 className="size-3 animate-spin text-slate-500" />
            <span className="text-slate-600 dark:text-slate-400">
              Looking up <span className="font-medium">{toolName}</span>...
            </span>
          </>
        ) : (
          <Collapsible open={isOpen} onOpenChange={setIsOpen} className="w-full">
            <div className="flex items-center gap-2">
              <span className="text-slate-600 dark:text-slate-400">
                Looked up <span className="font-medium">{toolName}</span>
              </span>
              <CollapsibleTrigger asChild>
                <button
                  className="ml-auto text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
                  aria-label={isOpen ? "Collapse result" : "Expand result"}
                >
                  {isOpen ? <ChevronUp className="size-3" /> : <ChevronDown className="size-3" />}
                </button>
              </CollapsibleTrigger>
            </div>
            <CollapsibleContent>
              <pre className="mt-2 overflow-x-auto rounded bg-slate-100 p-2 text-[10px] text-slate-700 dark:bg-slate-900 dark:text-slate-300">
                {JSON.stringify(inputData, null, 2)}
              </pre>
            </CollapsibleContent>
          </Collapsible>
        )}
      </div>
    </div>
  );
}
