"use client";

import { useEffect, useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { Loader2, CheckCircle2 } from "lucide-react";
import { getSubscription } from "@/app/(app)/org/[slug]/settings/billing/actions";

interface PayFastResultPollerProps {
  initialStatus: string;
}

function computeInitialState(
  result: string | null,
  initialStatus: string
): "idle" | "polling" | "success" | "timeout" | "cancelled" {
  if (result === "success" && initialStatus === "ACTIVE") return "success";
  if (result === "success") return "polling";
  if (result === "cancelled") return "cancelled";
  return "idle";
}

export function PayFastResultPoller({
  initialStatus,
}: PayFastResultPollerProps) {
  const searchParams = useSearchParams();
  const router = useRouter();
  const result = searchParams.get("result");

  const [pollingState, setPollingState] = useState(() =>
    computeInitialState(result, initialStatus)
  );

  useEffect(() => {
    if (result !== "success" || initialStatus === "ACTIVE") {
      return;
    }

    let elapsed = 0;
    const interval = setInterval(async () => {
      elapsed += 2000;

      try {
        const billing = await getSubscription();
        if (billing.status === "ACTIVE") {
          clearInterval(interval);
          setPollingState("success");
          router.refresh();
        }
      } catch {
        // Ignore polling errors, keep trying
      }

      if (elapsed >= 30000) {
        clearInterval(interval);
        setPollingState("timeout");
      }
    }, 2000);

    return () => clearInterval(interval);
  }, [result, initialStatus, router]);

  if (pollingState === "idle") {
    return null;
  }

  if (pollingState === "polling") {
    return (
      <div className="rounded-lg border border-teal-200 bg-teal-50 p-4 text-sm text-teal-800 dark:border-teal-800 dark:bg-teal-950 dark:text-teal-200">
        <div className="flex items-center gap-2">
          <Loader2 className="size-4 shrink-0 animate-spin" />
          <p>Processing your payment...</p>
        </div>
      </div>
    );
  }

  if (pollingState === "success") {
    return (
      <div className="rounded-lg border border-teal-200 bg-teal-50 p-4 text-sm text-teal-800 dark:border-teal-800 dark:bg-teal-950 dark:text-teal-200">
        <div className="flex items-center gap-2">
          <CheckCircle2 className="size-4 shrink-0" />
          <p>Payment successful! Your subscription is now active.</p>
        </div>
      </div>
    );
  }

  if (pollingState === "timeout") {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
        <div className="flex items-center gap-2">
          <Loader2 className="size-4 shrink-0" />
          <p>
            Your payment is still being processed. This may take a few minutes.
            Please refresh the page shortly.
          </p>
        </div>
      </div>
    );
  }

  if (pollingState === "cancelled") {
    return (
      <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300">
        <p>Payment was cancelled. You can try again when you are ready.</p>
      </div>
    );
  }

  return null;
}
