"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { PlaceInvestmentDialog } from "@/components/trust/PlaceInvestmentDialog";
import { RecordInvestmentInterestDialog } from "@/components/trust/RecordInvestmentInterestDialog";
import { WithdrawInvestmentDialog } from "@/components/trust/WithdrawInvestmentDialog";

interface InvestmentPageClientProps {
  accountId: string;
  variant: "place" | "actions";
  investmentId?: string;
  investmentPrincipal?: number;
  investmentInterestEarned?: number;
  currency?: string;
}

export function InvestmentPageClient({
  accountId,
  variant,
  investmentId,
  investmentPrincipal,
  investmentInterestEarned,
  currency,
}: InvestmentPageClientProps) {
  const router = useRouter();
  const [placeOpen, setPlaceOpen] = useState(false);
  const [interestOpen, setInterestOpen] = useState(false);
  const [withdrawOpen, setWithdrawOpen] = useState(false);

  function handleSuccess() {
    router.refresh();
  }

  if (variant === "place") {
    return (
      <>
        <Button onClick={() => setPlaceOpen(true)}>Place Investment</Button>
        <PlaceInvestmentDialog
          accountId={accountId}
          open={placeOpen}
          onOpenChange={setPlaceOpen}
          onSuccess={handleSuccess}
        />
      </>
    );
  }

  return (
    <div className="flex gap-2">
      <Button variant="outline" size="sm" onClick={() => setInterestOpen(true)}>
        Record Interest
      </Button>
      <Button variant="outline" size="sm" onClick={() => setWithdrawOpen(true)}>
        Withdraw
      </Button>
      {investmentId && (
        <>
          <RecordInvestmentInterestDialog
            investmentId={investmentId}
            open={interestOpen}
            onOpenChange={setInterestOpen}
            onSuccess={handleSuccess}
          />
          <WithdrawInvestmentDialog
            investmentId={investmentId}
            principal={investmentPrincipal ?? 0}
            interestEarned={investmentInterestEarned ?? 0}
            currency={currency ?? "ZAR"}
            open={withdrawOpen}
            onOpenChange={setWithdrawOpen}
            onSuccess={handleSuccess}
          />
        </>
      )}
    </div>
  );
}
