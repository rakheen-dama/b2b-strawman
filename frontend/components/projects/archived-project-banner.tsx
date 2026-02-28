"use client";

import { useState, useTransition } from "react";
import { Archive, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { reopenProject } from "@/app/(app)/org/[slug]/projects/actions";

interface ArchivedProjectBannerProps {
  slug: string;
  projectId: string;
  canRestore?: boolean;
}

export function ArchivedProjectBanner({
  slug,
  projectId,
  canRestore = false,
}: ArchivedProjectBannerProps) {
  const [isPending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);

  function handleRestore() {
    setError(null);
    startTransition(async () => {
      const result = await reopenProject(slug, projectId);
      if (!result.success) {
        setError(result.error ?? "Failed to restore project.");
      }
    });
  }

  return (
    <div
      className="space-y-2"
      data-testid="archived-project-banner"
      role="alert"
    >
      <div className="flex items-center justify-between rounded-lg border border-slate-300 bg-slate-100 px-4 py-3 dark:border-slate-700 dark:bg-slate-900">
        <div className="flex items-center gap-2">
          <Archive className="size-4 text-slate-500 dark:text-slate-400" />
          <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
            This project is archived. It is read-only.
          </p>
        </div>
        {canRestore && (
          <Button
            size="sm"
            variant="outline"
            onClick={handleRestore}
            disabled={isPending}
          >
            <RotateCcw className="mr-1.5 size-4" />
            {isPending ? "Restoring..." : "Restore"}
          </Button>
        )}
      </div>
      {error && (
        <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
      )}
    </div>
  );
}
