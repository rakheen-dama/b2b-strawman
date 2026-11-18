"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@b2mash/ui/button";
import { PlaceInvestmentDialog } from "@/components/trust/PlaceInvestmentDialog";
import { RecordInvestmentInterestDialog } from "@/components/trust/RecordInvestmentInterestDialog";
import { WithdrawInvestmentDialog } from "@/components/trust/WithdrawInvestmentDialog";
import type { TrustAccount } from "@/lib/types/trust";

interface InvestmentPageClientProps {
  /** The funding trust banking account. */
  accountId: string;
  variant: "place" | "actions";
  /** The firm's investment accounts, for the Place Investment picker (648A). */
  investmentAccounts?: TrustAccount[];
  investmentId?: string;
  /** What a withdrawal would return now (648A). */
  investmentBalance?: number;
  /** The investment's deposit date: a withdrawal cannot be dated earlier. */
  investmentDepositDate?: string;
  /** The investment account that holds it (its statement lines can carry the interest, 648B). */
  investmentAccountId?: string;
  /**
   * Interest is recorded on an ACTIVE section 86(4) investment only (648B: a section 86(3)
   * investment's interest is the Fund's; a MATURED one can still be withdrawn).
   */
  canRecordInterest?: boolean;
  currency?: string;
}

export function InvestmentPageClient({
  accountId,
  variant,
  investmentAccounts,
  investmentId,
  investmentBalance,
  investmentDepositDate,
  investmentAccountId,
  canRecordInterest = false,
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
          investmentAccounts={investmentAccounts ?? []}
          open={placeOpen}
          onOpenChange={setPlaceOpen}
          onSuccess={handleSuccess}
        />
      </>
    );
  }

  const showInterest = canRecordInterest && investmentAccountId !== undefined;

  return (
    <div className="flex flex-wrap gap-2">
      {showInterest && (
        <Button variant="outline" size="sm" onClick={() => setInterestOpen(true)}>
          Record interest
        </Button>
      )}
      <Button variant="outline" size="sm" onClick={() => setWithdrawOpen(true)}>
        Withdraw
      </Button>
      {investmentId && (
        <>
          {showInterest && (
            <RecordInvestmentInterestDialog
              investmentId={investmentId}
              investmentAccountId={investmentAccountId!}
              depositDate={investmentDepositDate ?? ""}
              currency={currency ?? "ZAR"}
              open={interestOpen}
              onOpenChange={setInterestOpen}
              onSuccess={handleSuccess}
            />
          )}
          <WithdrawInvestmentDialog
            investmentId={investmentId}
            balance={investmentBalance ?? 0}
            depositDate={investmentDepositDate ?? ""}
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
