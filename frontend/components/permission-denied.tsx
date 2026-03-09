"use client";

import Link from "next/link";
import { ShieldOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import { createMessages } from "@/lib/messages";

interface PermissionDeniedProps {
  featureName?: string;
  dashboardHref?: string;
}

export function PermissionDenied({
  featureName,
  dashboardHref = "../dashboard",
}: PermissionDeniedProps) {
  const { t } = createMessages("common");

  const heading = featureName
    ? `You don't have access to ${featureName}`
    : t("permission.denied.heading");

  return (
    <div className="flex flex-col items-center py-24 text-center gap-4">
      <ShieldOff className="size-12 text-slate-400" />
      <h2 className="font-display text-xl text-slate-900 dark:text-slate-100">
        {heading}
      </h2>
      <p className="max-w-md text-sm text-slate-600 dark:text-slate-400">
        {t("permission.denied.description")}
      </p>
      <Button asChild size="sm" variant="outline">
        <Link href={dashboardHref}>{t("permission.denied.cta")}</Link>
      </Button>
    </div>
  );
}
