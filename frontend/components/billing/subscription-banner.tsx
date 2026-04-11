"use client";

import { useState } from "react";
import Link from "next/link";
import { Info, AlertTriangle, XCircle, X } from "lucide-react";
import { Alert, AlertTitle, AlertDescription } from "@/components/ui/alert";
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

function getBannerConfig(billingResponse: BillingResponse, slug: string): BannerConfig | null {
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
        storageKey: `banner-dismissed-${slug}-TRIALING`,
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
        storageKey: `banner-dismissed-${slug}-PENDING_CANCELLATION`,
      };
    }

    // PAST_DUE requires payment action — intentionally non-dismissible despite
    // being "warning" variant (not "destructive") because the account is still
    // write-enabled but needs urgent attention.
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
        storageKey: `banner-dismissed-${slug}-PAST_DUE`,
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
        storageKey: `banner-dismissed-${slug}-${status}`,
      };

    case "LOCKED":
      return null;

    default:
      return null;
  }
}

export function SubscriptionBanner({ billingResponse, slug }: SubscriptionBannerProps) {
  const config = getBannerConfig(billingResponse, slug);

  // Track the storage key that the user last dismissed in this session.
  // When config changes (e.g. status transition), the new key won't match,
  // so the banner reappears — critical for non-dismissible transitions.
  const [dismissedKey, setDismissedKey] = useState<string | null>(null);

  const isDismissed =
    config?.dismissible === true &&
    (dismissedKey === config.storageKey ||
      (typeof window !== "undefined" && sessionStorage.getItem(config.storageKey) === "1"));

  if (!config || isDismissed) return null;

  function handleDismiss() {
    if (config) {
      sessionStorage.setItem(config.storageKey, "1");
      setDismissedKey(config.storageKey);
    }
  }

  return (
    <div className="px-6 pt-4 lg:px-10">
      <Alert variant={config.variant} className={cn("relative", config.className)}>
        {config.icon}
        <AlertTitle>{config.title}</AlertTitle>
        <AlertDescription>{config.description}</AlertDescription>
        {config.dismissible && (
          <button
            type="button"
            onClick={handleDismiss}
            className="ring-offset-background focus:ring-ring absolute top-3 right-3 rounded-sm opacity-70 transition-opacity hover:opacity-100 focus:ring-2 focus:ring-offset-2 focus:outline-none"
            aria-label="Dismiss banner"
          >
            <X className="size-4" />
          </button>
        )}
      </Alert>
    </div>
  );
}
