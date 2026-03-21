"use client";

import ReactMarkdown from "react-markdown";
import { cn } from "@/lib/utils";

interface AssistantMessageProps {
  message: {
    content: string;
  };
  isStreaming?: boolean;
}

export function AssistantMessage({
  message,
  isStreaming = false,
}: AssistantMessageProps) {
  return (
    <div
      className={cn(
        "max-w-[85%] rounded-lg px-3 py-2 text-sm",
        "bg-white border border-slate-200 text-slate-900",
        "dark:bg-slate-800 dark:border-slate-700 dark:text-slate-100",
      )}
    >
      <div className="prose prose-sm prose-slate max-w-none dark:prose-invert">
        <ReactMarkdown>{message.content}</ReactMarkdown>
      </div>
      {isStreaming && (
        <span
          className="inline-block text-teal-600 ml-0.5 font-normal animate-pulse"
          aria-hidden="true"
        >
          |
        </span>
      )}
    </div>
  );
}
