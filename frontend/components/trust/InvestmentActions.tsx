"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Plus, DollarSign, ArrowUpFromLine } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { PlaceInvestmentDialog } from "./PlaceInvestmentDialog";
import { RecordInvestmentInterestDialog } from "./RecordInvestmentInterestDialog";
import { WithdrawInvestmentDialog } from "./WithdrawInvestmentDialog";

interface HeaderActionsProps {
  accountId: string;
  variant?: "header";
}

interface RowActionsProps {
  investmentId: string;
  principal: number;
  interestEarned: number;
  currency: string;
  variant: "row";
}

type InvestmentActionsProps = HeaderActionsProps | RowActionsProps;

export function InvestmentActions(props: InvestmentActionsProps) {
  const [placeOpen, setPlaceOpen] = useState(false);
  const [interestOpen, setInterestOpen] = useState(false);
  const [withdrawOpen, setWithdrawOpen] = useState(false);

  if (props.variant === "row") {
    return (
      <>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button size="sm" variant="outline" className="h-7 text-xs">
              Actions
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={() => setInterestOpen(true)}>
              <DollarSign className="mr-1.5 size-3" />
              Record Interest
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => setWithdrawOpen(true)}>
              <ArrowUpFromLine className="mr-1.5 size-3" />
              Withdraw
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        <RecordInvestmentInterestDialog
          investmentId={props.investmentId}
          open={interestOpen}
          onOpenChange={setInterestOpen}
        />
        <WithdrawInvestmentDialog
          investmentId={props.investmentId}
          principal={props.principal}
          interestEarned={props.interestEarned}
          currency={props.currency}
          open={withdrawOpen}
          onOpenChange={setWithdrawOpen}
        />
      </>
    );
  }

  return (
    <>
      <Button size="sm" onClick={() => setPlaceOpen(true)}>
        <Plus className="mr-1.5 size-4" />
        Place Investment
      </Button>

      <PlaceInvestmentDialog
        accountId={props.accountId}
        open={placeOpen}
        onOpenChange={setPlaceOpen}
      />
    </>
  );
}
