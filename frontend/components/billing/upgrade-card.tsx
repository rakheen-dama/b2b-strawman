"use client";

import { useRouter } from "next/navigation";
import { Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { UpgradeConfirmDialog } from "@/components/billing/upgrade-confirm-dialog";
import { upgradeToPro } from "@/app/(app)/org/[slug]/settings/billing/actions";

interface UpgradeCardProps {
  slug: string;
}

export function UpgradeCard({ slug }: UpgradeCardProps) {
  const router = useRouter();

  async function handleUpgrade() {
    const result = await upgradeToPro(slug);
    if (!result.success) {
      throw new Error(result.error ?? "Failed to upgrade plan.");
    }
    router.refresh();
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Sparkles className="h-5 w-5" />
          Upgrade to Pro
        </CardTitle>
        <CardDescription>
          Unlock dedicated infrastructure, higher member limits, and priority
          support for your team.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <UpgradeConfirmDialog
          onConfirm={handleUpgrade}
          trigger={<Button>Upgrade to Pro</Button>}
        />
      </CardContent>
    </Card>
  );
}
