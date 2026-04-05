"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Plus, Calculator } from "lucide-react";
import { InterestRunWizard } from "./InterestRunWizard";
import { LpffRateDialog } from "./LpffRateDialog";

interface InterestActionsProps {
  accountId: string;
}

export function InterestActions({ accountId }: InterestActionsProps) {
  const [wizardOpen, setWizardOpen] = useState(false);
  const [rateOpen, setRateOpen] = useState(false);

  return (
    <>
      <div className="flex items-center gap-2">
        <Button size="sm" variant="outline" onClick={() => setRateOpen(true)}>
          <Plus className="mr-1.5 size-4" />
          Add Rate
        </Button>
        <Button size="sm" onClick={() => setWizardOpen(true)}>
          <Calculator className="mr-1.5 size-4" />
          New Interest Run
        </Button>
      </div>

      <LpffRateDialog
        accountId={accountId}
        open={rateOpen}
        onOpenChange={setRateOpen}
      />
      <InterestRunWizard
        accountId={accountId}
        open={wizardOpen}
        onOpenChange={setWizardOpen}
      />
    </>
  );
}
