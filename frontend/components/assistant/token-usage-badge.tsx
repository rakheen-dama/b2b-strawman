"use client";

import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";

interface TokenUsageBadgeProps {
  inputTokens: number;
  outputTokens: number;
}

function formatTokens(count: number): string {
  if (count >= 1000) {
    return `~${(count / 1000).toFixed(1)}K`;
  }
  return `~${count}`;
}

export function TokenUsageBadge({ inputTokens, outputTokens }: TokenUsageBadgeProps) {
  const total = inputTokens + outputTokens;

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="cursor-default rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500 dark:bg-slate-800 dark:text-slate-400">
            {formatTokens(total)} tokens
          </span>
        </TooltipTrigger>
        <TooltipContent>
          <p>
            Input: {inputTokens} / Output: {outputTokens}
          </p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
