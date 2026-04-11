"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { CreateTrustAccountDialog } from "@/components/trust/CreateTrustAccountDialog";

interface AddTrustAccountButtonProps {
  variant?: "primary" | "empty-state";
  label?: string;
}

export function AddTrustAccountButton({
  variant = "primary",
  label = "Add Account",
}: AddTrustAccountButtonProps) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button
        type="button"
        size="sm"
        variant={variant === "empty-state" ? "outline" : "accent"}
        onClick={() => setOpen(true)}
        data-testid="add-trust-account-button"
      >
        <Plus className="size-3.5" />
        {label}
      </Button>
      <CreateTrustAccountDialog open={open} onOpenChange={setOpen} />
    </>
  );
}
