"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { CreateTrustAccountDialog } from "./CreateTrustAccountDialog";

interface AddTrustAccountButtonProps {
  variant?: "primary" | "empty-state";
  label?: string;
}

export function AddTrustAccountButton({
  variant = "primary",
  label = "Add Account",
}: AddTrustAccountButtonProps) {
  const [open, setOpen] = useState(false);

  const className =
    variant === "empty-state"
      ? "inline-flex items-center gap-1.5 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm font-medium text-slate-900 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:hover:bg-slate-800"
      : "inline-flex items-center gap-1.5 rounded-md bg-slate-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-slate-800 dark:bg-slate-100 dark:text-slate-900 dark:hover:bg-slate-200";

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={className}
        data-testid="add-trust-account-button"
      >
        <Plus className="size-3.5" />
        {label}
      </button>
      <CreateTrustAccountDialog open={open} onOpenChange={setOpen} />
    </>
  );
}
