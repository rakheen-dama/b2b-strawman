"use client";

import ReactMarkdown from "react-markdown";
import { cn } from "@/lib/utils";

interface AssistantMessageProps {
  message: {
    content: string;
  };
  isStreaming?: boolean;
}

export function AssistantMessage({ message, isStreaming = false }: AssistantMessageProps) {
  return (
    <div
      className={cn(
        "max-w-[85%] rounded-lg px-3 py-2 text-sm",
        "border border-slate-200 bg-white text-slate-900",
        "dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
      )}
    >
      <div className="prose prose-sm prose-slate dark:prose-invert max-w-none">
        <ReactMarkdown>{message.content}</ReactMarkdown>
      </div>
      {isStreaming && (
        <span
          className="ml-0.5 inline-block animate-pulse font-normal text-teal-600"
          aria-hidden="true"
        >
          |
        </span>
      )}
    </div>
  );
}
