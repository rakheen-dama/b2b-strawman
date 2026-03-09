"use client";

import { useState } from "react";
import { CheckCircle2, Circle, X } from "lucide-react";
import { createMessages } from "@/lib/messages";
import { useOnboardingProgress } from "@/hooks/use-onboarding-progress";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardAction,
  CardContent,
} from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverTrigger,
  PopoverContent,
} from "@/components/ui/popover";

const { t } = createMessages("getting-started");

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

  if (loading || dismissed || allComplete) {
    return null;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("card.title")}</CardTitle>
        <CardDescription>{t("card.subtitle")}</CardDescription>
        <CardAction>
          <Popover open={dismissOpen} onOpenChange={setDismissOpen}>
            <PopoverTrigger asChild>
              <Button variant="ghost" size="icon" aria-label={t("card.dismiss")}>
                <X className="size-4" />
              </Button>
            </PopoverTrigger>
            <PopoverContent align="end" className="w-64">
              <p className="text-sm">{t("card.dismissConfirm")}</p>
              <div className="mt-3 flex justify-end gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setDismissOpen(false)}
                >
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
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-1.5">
          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">
              {t("card.progress", {
                completed: String(completedCount),
                total: String(totalCount),
              })}
            </span>
            <span className="font-medium">{percentComplete}%</span>
          </div>
          <Progress value={percentComplete} className="h-2" />
        </div>
        <ul className="space-y-3">
          {steps.map((step) => {
            const key = step.code.toLowerCase();
            return (
              <li key={step.code} className="flex items-start gap-3">
                {step.completed ? (
                  <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-teal-500" />
                ) : (
                  <Circle className="mt-0.5 size-5 shrink-0 text-slate-300" />
                )}
                <div>
                  <p className="text-sm font-medium">{t(`${key}.label`)}</p>
                  <p className="text-muted-foreground text-xs">
                    {t(`${key}.description`)}
                  </p>
                </div>
              </li>
            );
          })}
        </ul>
      </CardContent>
    </Card>
  );
}
