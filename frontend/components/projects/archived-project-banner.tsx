"use client";

import { useTransition } from "react";
import { Archive, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { reopenProject } from "@/app/(app)/org/[slug]/projects/actions";

interface ArchivedProjectBannerProps {
  slug: string;
  projectId: string;
}

export function ArchivedProjectBanner({
  slug,
  projectId,
}: ArchivedProjectBannerProps) {
  const [isPending, startTransition] = useTransition();

  function handleRestore() {
    startTransition(async () => {
      await reopenProject(slug, projectId);
    });
  }

  return (
    <div
      className="flex items-center justify-between rounded-lg border border-slate-300 bg-slate-100 px-4 py-3 dark:border-slate-700 dark:bg-slate-900"
      data-testid="archived-project-banner"
      role="alert"
    >
      <div className="flex items-center gap-2">
        <Archive className="size-4 text-slate-500 dark:text-slate-400" />
        <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
          This project is archived. It is read-only.
        </p>
      </div>
      <Button
        size="sm"
        variant="outline"
        onClick={handleRestore}
        disabled={isPending}
      >
        <RotateCcw className="mr-1.5 size-4" />
        {isPending ? "Restoring..." : "Restore"}
      </Button>
    </div>
  );
}
