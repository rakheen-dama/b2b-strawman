"use client";

import { useState } from "react";
import { FileText } from "lucide-react";
import { Button } from "@/components/ui/button";
import { StatementOfAccountDialog } from "@/components/legal/statement-of-account-dialog";
import { useCapabilities } from "@/lib/capabilities";
import { useOrgProfile } from "@/lib/org-profile";
import type { ProjectStatus } from "@/lib/types";

interface GenerateStatementOfAccountActionProps {
  slug: string;
  projectId: string;
  projectName: string;
  projectStatus: ProjectStatus;
}

export function GenerateStatementOfAccountAction({
  slug,
  projectId,
  projectName,
  projectStatus,
}: GenerateStatementOfAccountActionProps) {
  const [open, setOpen] = useState(false);
  const { isModuleEnabled } = useOrgProfile();
  const { hasCapability } = useCapabilities();

  // Co-gated with disbursements module (per ADR-250).
  if (!isModuleEnabled("disbursements")) return null;
  // Hide for ARCHIVED matters. SoA is fine on ACTIVE, COMPLETED, CLOSED (informational).
  if (projectStatus === "ARCHIVED") return null;
  if (!hasCapability("GENERATE_STATEMENT_OF_ACCOUNT")) return null;

  return (
    <>
      <Button
        size="sm"
        variant="outline"
        onClick={() => setOpen(true)}
        data-testid="generate-statement-btn"
      >
        <FileText className="mr-1.5 size-4" />
        Generate Statement of Account
      </Button>
      <StatementOfAccountDialog
        slug={slug}
        projectId={projectId}
        projectName={projectName}
        open={open}
        onOpenChange={setOpen}
      />
    </>
  );
}
