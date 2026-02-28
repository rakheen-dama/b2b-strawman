"use client";

import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * Floating "Log Time" button pinned to bottom-right.
 * Currently a placeholder — full log-time dialog
 * will be wired in a subsequent task.
 */
export function TimeEntryFab() {
  return (
    <div className="fixed bottom-6 right-6 z-40">
      <Button
        size="lg"
        className="gap-2 rounded-full bg-teal-600 px-5 text-white shadow-lg hover:bg-teal-600/90"
        onClick={() => {
          // TODO: Open log-time dialog
        }}
      >
        <Plus className="size-4" />
        Log Time
      </Button>
    </div>
  );
}
