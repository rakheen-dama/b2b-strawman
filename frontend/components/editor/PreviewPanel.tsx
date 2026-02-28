"use client";

import { cn } from "@/lib/utils";

interface PreviewPanelProps {
  html: string;
  className?: string;
}

/**
 * Document preview panel that renders HTML in a sandboxed iframe
 * with A4 document-like styling.
 */
export function PreviewPanel({ html, className }: PreviewPanelProps) {
  return (
    <div
      className={cn(
        "mx-auto max-w-[210mm] rounded-lg border border-slate-200 bg-white shadow-md dark:border-slate-800",
        className,
      )}
    >
      <iframe
        sandbox=""
        srcDoc={html}
        className="h-[500px] w-full bg-white"
        title="Document Preview"
      />
    </div>
  );
}
