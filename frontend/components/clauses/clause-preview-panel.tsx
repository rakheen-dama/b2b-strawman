"use client";

interface ClausePreviewPanelProps {
  html: string | null;
  isLoading: boolean;
}

export function ClausePreviewPanel({ html, isLoading }: ClausePreviewPanelProps) {
  if (isLoading) {
    return (
      <div className="flex h-[300px] items-center justify-center rounded-lg border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
        <p className="text-sm text-slate-500">Generating preview...</p>
      </div>
    );
  }

  if (!html) {
    return null;
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 dark:border-slate-800">
      <iframe
        sandbox=""
        srcDoc={html}
        className="h-[300px] w-full bg-white"
        title="Clause Preview"
      />
    </div>
  );
}
