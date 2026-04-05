"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { InterestRunWizard } from "@/components/trust/InterestRunWizard";
import { LpffRateDialog } from "@/components/trust/LpffRateDialog";

interface InterestPageClientProps {
  accountId: string;
  variant: "wizard" | "rate";
  canApprove?: boolean;
  currency?: string;
}

export function InterestPageClient({
  accountId,
  variant,
  canApprove,
  currency,
}: InterestPageClientProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);

  function handleSuccess() {
    router.refresh();
  }

  if (variant === "wizard") {
    return (
      <>
        <Button onClick={() => setOpen(true)}>New Interest Run</Button>
        <InterestRunWizard
          accountId={accountId}
          open={open}
          onOpenChange={setOpen}
          onSuccess={handleSuccess}
          canApprove={canApprove ?? false}
          currency={currency ?? "ZAR"}
        />
      </>
    );
  }

  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        Add Rate
      </Button>
      <LpffRateDialog
        accountId={accountId}
        open={open}
        onOpenChange={setOpen}
        onSuccess={handleSuccess}
      />
    </>
  );
}
