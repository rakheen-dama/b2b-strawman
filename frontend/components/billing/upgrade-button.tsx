"use client";

import { Button } from "@/components/ui/button";
import { UpgradeConfirmDialog } from "@/components/billing/upgrade-confirm-dialog";
import { upgradeToPro } from "@/app/(app)/org/[slug]/settings/billing/actions";

interface UpgradeButtonProps {
  slug: string;
  variant?: "accent" | "default";
  className?: string;
}

export function UpgradeButton({
  slug,
  variant = "accent",
  className,
}: UpgradeButtonProps) {
  async function handleUpgrade() {
    const result = await upgradeToPro(slug);
    if (!result.success) {
      throw new Error(result.error ?? "Failed to upgrade plan.");
    }
  }

  return (
    <UpgradeConfirmDialog
      onConfirm={handleUpgrade}
      trigger={
        <Button variant={variant} className={className}>
          Upgrade to Pro
        </Button>
      }
    />
  );
}
