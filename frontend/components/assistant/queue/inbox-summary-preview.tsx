"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export interface InboxSummaryPreviewProps {
  invocationId: string;
  proposedOutput: Record<string, unknown> | null;
}

export function InboxSummaryPreview({ proposedOutput }: InboxSummaryPreviewProps) {
  if (!proposedOutput) {
    return <p className="text-sm text-slate-500">No summary output available.</p>;
  }

  const summary = (proposedOutput.summary as string) ?? "";
  const highlights = (proposedOutput.highlights as string[]) ?? [];

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium">Inbox Summary</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {summary && (
          <p className="text-sm whitespace-pre-wrap text-slate-700 dark:text-slate-300">
            {summary}
          </p>
        )}
        {highlights.length > 0 && (
          <ul className="list-disc space-y-1 pl-4">
            {highlights.map((h, i) => (
              <li key={i} className="text-sm text-slate-600 dark:text-slate-400">
                {h}
              </li>
            ))}
          </ul>
        )}
        {!summary && highlights.length === 0 && (
          <pre className="max-h-48 overflow-auto rounded bg-slate-50 p-2 text-xs text-slate-500 dark:bg-slate-800">
            {JSON.stringify(proposedOutput, null, 2)}
          </pre>
        )}
      </CardContent>
    </Card>
  );
}
