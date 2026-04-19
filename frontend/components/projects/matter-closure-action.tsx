"use client";

import { useState } from "react";
import { Gavel } from "lucide-react";
import { Button } from "@/components/ui/button";
import { MatterClosureDialog } from "@/components/legal/matter-closure-dialog";
import { useCapabilities } from "@/lib/capabilities";
import { useOrgProfile } from "@/lib/org-profile";
import type { ProjectStatus } from "@/lib/types";

interface MatterClosureActionProps {
  slug: string;
  projectId: string;
  projectName: string;
  projectStatus: ProjectStatus;
}

export function MatterClosureAction({
  slug,
  projectId,
  projectName,
  projectStatus,
}: MatterClosureActionProps) {
  const [open, setOpen] = useState(false);
  const { isModuleEnabled } = useOrgProfile();
  const { hasCapability } = useCapabilities();

  if (!isModuleEnabled("matter_closure")) return null;
  if (projectStatus !== "ACTIVE" && projectStatus !== "COMPLETED") return null;
  if (!hasCapability("CLOSE_MATTER")) return null;

  return (
    <>
      <Button
        size="sm"
        variant="outline"
        onClick={() => setOpen(true)}
        data-testid="close-matter-btn"
      >
        <Gavel className="mr-1.5 size-4" />
        Close Matter
      </Button>
      <MatterClosureDialog
        slug={slug}
        projectId={projectId}
        projectName={projectName}
        open={open}
        onOpenChange={setOpen}
      />
    </>
  );
}
