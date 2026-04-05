"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { InterestRunWizard } from "@/components/trust/InterestRunWizard";
import { LpffRateDialog } from "@/components/trust/LpffRateDialog";

interface InterestPageClientProps {
  accountId: string;
  variant: "wizard" | "rate";
}

export function InterestPageClient({
  accountId,
  variant,
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
