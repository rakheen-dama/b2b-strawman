"use client";

import { useState } from "react";
import Link from "next/link";
import { Info, AlertTriangle, XCircle, X } from "lucide-react";
import { Alert, AlertTitle, AlertDescription } from "@/components/ui/alert";
import { useSubscription } from "@/lib/subscription-context";
import { computeDaysRemaining, formatDate } from "@/lib/billing-utils";
import { cn } from "@/lib/utils";
import type { BillingResponse } from "@/lib/internal-api";

interface SubscriptionBannerProps {
  billingResponse: BillingResponse;
  slug: string;
}

type BannerConfig = {
  variant: "default" | "destructive" | "warning";
  className?: string;
  icon: React.ReactNode;
  title: string;
  description: React.ReactNode;
  dismissible: boolean;
  storageKey: string;
};

function getBannerConfig(
  billingResponse: BillingResponse,
  slug: string,
): BannerConfig | null {
  const { status } = billingResponse;

  switch (status) {
    case "TRIALING": {
      if (!billingResponse.trialEndsAt) return null;
      const daysRemaining = computeDaysRemaining(billingResponse.trialEndsAt);
      if (daysRemaining > 7) return null;
      return {
        variant: "default",
        className:
          "border-blue-200 bg-blue-50 text-blue-800 dark:border-blue-800 dark:bg-blue-950 dark:text-blue-200",
        icon: <Info className="size-4" />,
        title: "Trial ending soon",
        description: (
          <>
            Your trial ends in {daysRemaining} day{daysRemaining !== 1 ? "s" : ""} &mdash;{" "}
            <Link
              href={`/org/${slug}/settings/billing`}
              className="font-medium underline underline-offset-4"
            >
              Subscribe now
            </Link>
          </>
        ),
        dismissible: true,
        storageKey: "banner-dismissed-TRIALING",
      };
    }

    case "ACTIVE":
      return null;

    case "PENDING_CANCELLATION": {
      const endDate = billingResponse.currentPeriodEnd
        ? formatDate(billingResponse.currentPeriodEnd)
        : null;
      return {
        variant: "warning",
        icon: <AlertTriangle className="size-4" />,
        title: "Subscription ending",
        description: endDate ? (
          <>
            Your subscription ends on {endDate}.{" "}
            <Link
              href={`/org/${slug}/settings/billing`}
              className="font-medium underline underline-offset-4"
            >
              Resubscribe
            </Link>
          </>
        ) : (
          <>
            Your subscription is ending soon.{" "}
            <Link
              href={`/org/${slug}/settings/billing`}
              className="font-medium underline underline-offset-4"
            >
              Resubscribe
            </Link>
          </>
        ),
        dismissible: true,
        storageKey: "banner-dismissed-PENDING_CANCELLATION",
      };
    }

    case "PAST_DUE":
      return {
        variant: "warning",
        icon: <AlertTriangle className="size-4" />,
        title: "Payment failed",
        description: (
          <>
            Payment failed &mdash;{" "}
            <Link
              href={`/org/${slug}/settings/billing`}
              className="font-medium underline underline-offset-4"
            >
              update your payment method
            </Link>
          </>
        ),
        dismissible: false,
        storageKey: "banner-dismissed-PAST_DUE",
      };

    case "GRACE_PERIOD":
    case "EXPIRED":
    case "SUSPENDED":
      return {
        variant: "destructive",
        icon: <XCircle className="size-4" />,
        title: "Read-only mode",
        description: (
          <>
            Read-only mode &mdash;{" "}
            <Link
              href={`/org/${slug}/settings/billing`}
              className="font-medium underline underline-offset-4"
            >
              subscribe to regain full access
            </Link>
          </>
        ),
        dismissible: false,
        storageKey: `banner-dismissed-${status}`,
      };

    case "LOCKED":
      return null;

    default:
      return null;
  }
}

export function SubscriptionBanner({
  billingResponse,
  slug,
}: SubscriptionBannerProps) {
  // Read status from context for consistency (context recomputes derived values)
  const { status } = useSubscription();

  const config = getBannerConfig({ ...billingResponse, status }, slug);

  const [dismissed, setDismissed] = useState(() => {
    if (!config?.dismissible) return false;
    return (
      typeof window !== "undefined" &&
      sessionStorage.getItem(config.storageKey) === "1"
    );
  });

  if (!config || dismissed) return null;

  function handleDismiss() {
    if (config) {
      sessionStorage.setItem(config.storageKey, "1");
    }
    setDismissed(true);
  }

  return (
    <div className="px-6 pt-4 lg:px-10">
      <Alert
        variant={config.variant}
        className={cn("relative", config.className)}
      >
        {config.icon}
        <AlertTitle>{config.title}</AlertTitle>
        <AlertDescription>{config.description}</AlertDescription>
        {config.dismissible && (
          <button
            type="button"
            onClick={handleDismiss}
            className="absolute right-3 top-3 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
            aria-label="Dismiss banner"
          >
            <X className="size-4" />
          </button>
        )}
      </Alert>
    </div>
  );
}
