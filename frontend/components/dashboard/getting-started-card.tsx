"use client";

import { useState } from "react";
import { CheckCircle2, X } from "lucide-react";
import { createMessages } from "@/lib/messages";
import { HelpTip } from "@/components/help-tip";
import { useOnboardingProgress } from "@/hooks/use-onboarding-progress";
import { Progress } from "@/components/ui/progress";
import { Button } from "@b2mash/ui/button";
import { Popover, PopoverTrigger, PopoverContent } from "@/components/ui/popover";

const { t } = createMessages("getting-started");

/** Onboarding step completed once the firm has opened its first matter. */
const FIRST_MATTER_STEP = "CREATE_PROJECT";

/** Shown until the firm's first matter exists (arch §12.6.3), unless dismissed or all done. */
export function GettingStartedCard() {
  const {
    steps,
    completedCount,
    totalCount,
    percentComplete,
    allComplete,
    dismissed,
    loading,
    dismiss,
  } = useOnboardingProgress();
  const [dismissOpen, setDismissOpen] = useState(false);

  const hasFirstMatter = steps.some((s) => s.code === FIRST_MATTER_STEP && s.completed);

  if (loading || dismissed || allComplete || hasFirstMatter) {
    return null;
  }

  return (
    <div
      data-testid="getting-started-banner"
      className="flex items-center gap-4 rounded-lg border border-teal-600/20 bg-teal-600/10 px-4 py-2.5"
    >
      <CheckCircle2 className="size-4 shrink-0 text-teal-600" />
      <div className="flex min-w-0 flex-1 items-center gap-3">
        <span className="text-sm font-medium text-teal-900 dark:text-teal-100">
          {t("card.title")}
        </span>
        <HelpTip code="onboarding.setup" />
        <span className="text-xs text-teal-700 dark:text-teal-300">
          {t("card.progress", {
            completed: String(completedCount),
            total: String(totalCount),
          })}
        </span>
        <Progress value={percentComplete} className="h-1.5 w-24" />
      </div>
      <Popover open={dismissOpen} onOpenChange={setDismissOpen}>
        <PopoverTrigger asChild>
          <Button variant="ghost" size="icon" className="size-7" aria-label={t("card.dismiss")}>
            <X className="size-3.5" />
          </Button>
        </PopoverTrigger>
        <PopoverContent align="end" className="w-64">
          <p className="text-sm">{t("card.dismissConfirm")}</p>
          <div className="mt-3 flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={() => setDismissOpen(false)}>
              {t("card.dismissCancel")}
            </Button>
            <Button
              variant="destructive"
              size="sm"
              onClick={async () => {
                await dismiss();
                setDismissOpen(false);
              }}
            >
              {t("card.dismiss")}
            </Button>
          </div>
        </PopoverContent>
      </Popover>
    </div>
  );
}
