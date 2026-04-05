"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Plus } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { RecordDepositDialog } from "./RecordDepositDialog";
import { RecordPaymentDialog } from "./RecordPaymentDialog";
import { RecordTransferDialog } from "./RecordTransferDialog";
import { RecordFeeTransferDialog } from "./RecordFeeTransferDialog";
import { RecordRefundDialog } from "./RecordRefundDialog";

interface TransactionActionsProps {
  accountId: string;
  slug: string;
}

export function TransactionActions({
  accountId,
  slug,
}: TransactionActionsProps) {
  const [depositOpen, setDepositOpen] = useState(false);
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [transferOpen, setTransferOpen] = useState(false);
  const [feeTransferOpen, setFeeTransferOpen] = useState(false);
  const [refundOpen, setRefundOpen] = useState(false);

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button size="sm" data-testid="record-transaction-button">
            <Plus className="mr-1.5 size-4" />
            Record Transaction
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onClick={() => setDepositOpen(true)}>
            Record Deposit
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => setPaymentOpen(true)}>
            Record Payment
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => setTransferOpen(true)}>
            Record Transfer
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => setFeeTransferOpen(true)}>
            Record Fee Transfer
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => setRefundOpen(true)}>
            Record Refund
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      <RecordDepositDialog
        accountId={accountId}
        slug={slug}
        open={depositOpen}
        onOpenChange={setDepositOpen}
      />
      <RecordPaymentDialog
        accountId={accountId}
        open={paymentOpen}
        onOpenChange={setPaymentOpen}
      />
      <RecordTransferDialog
        accountId={accountId}
        open={transferOpen}
        onOpenChange={setTransferOpen}
      />
      <RecordFeeTransferDialog
        accountId={accountId}
        open={feeTransferOpen}
        onOpenChange={setFeeTransferOpen}
      />
      <RecordRefundDialog
        accountId={accountId}
        open={refundOpen}
        onOpenChange={setRefundOpen}
      />
    </>
  );
}
