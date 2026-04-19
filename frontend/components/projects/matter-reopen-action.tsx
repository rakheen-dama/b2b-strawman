"use client";

import { useState } from "react";
import { RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { MatterReopenDialog } from "@/components/legal/matter-reopen-dialog";
import { useCapabilities } from "@/lib/capabilities";
import { useOrgProfile } from "@/lib/org-profile";
import type { ProjectStatus } from "@/lib/types";

interface MatterReopenActionProps {
  slug: string;
  projectId: string;
  projectName: string;
  projectStatus: ProjectStatus;
}

export function MatterReopenAction({
  slug,
  projectId,
  projectName,
  projectStatus,
}: MatterReopenActionProps) {
  const [open, setOpen] = useState(false);
  const { isModuleEnabled } = useOrgProfile();
  const { hasCapability } = useCapabilities();

  if (!isModuleEnabled("matter_closure")) return null;
  if (projectStatus !== "CLOSED") return null;
  if (!hasCapability("CLOSE_MATTER")) return null;

  return (
    <>
      <Button
        size="sm"
        variant="outline"
        onClick={() => setOpen(true)}
        data-testid="reopen-matter-btn"
      >
        <RotateCcw className="mr-1.5 size-4" />
        Reopen Matter
      </Button>
      <MatterReopenDialog
        slug={slug}
        projectId={projectId}
        projectName={projectName}
        open={open}
        onOpenChange={setOpen}
      />
    </>
  );
}
