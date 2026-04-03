import { Suspense } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  CheckCircle2,
  ChevronLeft,
  Clock,
  Info,
  Lock,
  Users,
  XCircle,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { SubscribeButton } from "@/components/billing/subscribe-button";
import { CancelConfirmDialog } from "@/components/billing/cancel-confirm-dialog";
import { PaymentHistory } from "@/components/billing/payment-history";
import { TrialCountdown } from "@/components/billing/trial-countdown";
import { GraceCountdown } from "@/components/billing/grace-countdown";
import { PayFastResultPoller } from "@/components/billing/payfast-result-poller";
import { MethodBadge } from "@/components/billing/method-badge";
import { getSubscription } from "@/app/(app)/org/[slug]/settings/billing/actions";
import {
  formatAmount,
  formatDate,
  computeDaysRemaining,
} from "@/lib/billing-utils";

function formatDateNullable(isoString: string | null): string {
  if (!isoString) return "\u2014";
  return formatDate(isoString);
}

function daysRemaining(isoString: string | null): number {
  if (!isoString) return 0;
  return computeDaysRemaining(isoString);
}

type BadgeVariant = "neutral" | "success" | "warning" | "destructive";

function statusBadgeVariant(status: string): BadgeVariant {
  switch (status) {
    case "TRIALING":
      return "neutral";
    case "ACTIVE":
      return "success";
    case "PENDING_CANCELLATION":
    case "PAST_DUE":
      return "warning";
    case "GRACE_PERIOD":
    case "EXPIRED":
    case "SUSPENDED":
    case "LOCKED":
      return "destructive";
    default:
      return "neutral";
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case "TRIALING":
      return "Trial";
    case "ACTIVE":
      return "Active";
    case "PENDING_CANCELLATION":
      return "Cancelling";
    case "PAST_DUE":
      return "Past Due";
    case "GRACE_PERIOD":
      return "Grace Period";
    case "EXPIRED":
      return "Expired";
    case "SUSPENDED":
      return "Suspended";
    case "LOCKED":
      return "Locked";
    default:
      return status;
  }
}

export default async function BillingPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ result?: string }>;
}) {
  const { slug } = await params;
  const { result } = await searchParams;
  const billing = await getSubscription();

  const { maxMembers, currentMembers } = billing.limits;
  const amount = formatAmount(billing.monthlyAmountCents, billing.currency);

  return (
    <div className="space-y-8">
      {/* Back link */}
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Settings
      </Link>

      {/* Page header */}
      <div className="flex items-center gap-3">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Billing
        </h1>
        <Badge variant={statusBadgeVariant(billing.status)}>
          {statusLabel(billing.status)}
        </Badge>
        {billing.adminManaged && (
          <MethodBadge method={billing.billingMethod} />
        )}
      </div>

      {/* Admin-managed info card */}
      {billing.adminManaged && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Info className="size-5 text-teal-600" />
              Managed Account
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-slate-600 dark:text-slate-400">
              Your account is managed by your administrator.
              {billing.status === "GRACE_PERIOD" &&
                " Contact your administrator to restore access."}
            </p>
          </CardContent>
        </Card>
      )}

      {/* Admin-managed LOCKED override */}
      {billing.adminManaged && billing.status === "LOCKED" && (
        <div className="flex min-h-[50vh] items-center justify-center">
          <Card className="max-w-md text-center">
            <CardHeader>
              <CardTitle className="flex items-center justify-center gap-2">
                <Lock className="size-5 text-red-500" />
                Account Locked
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm text-slate-600 dark:text-slate-400">
                Contact your administrator to restore access.
              </p>
            </CardContent>
          </Card>
        </div>
      )}

      {/* === PayFast / self-service UI — hidden for admin-managed tenants === */}
      {!billing.adminManaged && (
        <>
          {/* PayFast redirect handling with polling */}
          {(result === "success" || result === "cancelled") && (
            <Suspense fallback={null}>
              <PayFastResultPoller initialStatus={billing.status} />
            </Suspense>
          )}

          {/* TRIALING */}
          {billing.status === "TRIALING" && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Clock className="size-5 text-teal-600" />
                  Trial Period
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {billing.trialEndsAt && (
                  <TrialCountdown trialEndsAt={billing.trialEndsAt} />
                )}
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  Subscribe to continue using all features after your trial ends on{" "}
                  {formatDateNullable(billing.trialEndsAt)}.
                </p>
                <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
                  <Users className="size-4" />
                  {currentMembers} of {maxMembers} members
                </div>
                <p className="text-lg font-semibold text-slate-950 dark:text-slate-50">
                  {amount}
                  <span className="text-sm font-normal text-slate-500">
                    /month
                  </span>
                </p>
                {billing.canSubscribe && <SubscribeButton />}
              </CardContent>
            </Card>
          )}

          {/* ACTIVE */}
          {billing.status === "ACTIVE" && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <CheckCircle2 className="size-5 text-teal-600" />
                  Active Subscription
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  Your subscription is active. Next billing date:{" "}
                  <span className="font-semibold text-slate-950 dark:text-slate-50">
                    {formatDateNullable(billing.nextBillingAt)}
                  </span>
                </p>
                <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
                  <Users className="size-4" />
                  {currentMembers} of {maxMembers} members
                </div>
                <p className="text-lg font-semibold text-slate-950 dark:text-slate-50">
                  {amount}
                  <span className="text-sm font-normal text-slate-500">
                    /month
                  </span>
                </p>
                {billing.canCancel && billing.currentPeriodEnd && (
                  <CancelConfirmDialog
                    currentPeriodEnd={billing.currentPeriodEnd}
                  />
                )}
              </CardContent>
            </Card>
          )}

          {/* PENDING_CANCELLATION */}
          {billing.status === "PENDING_CANCELLATION" && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <AlertTriangle className="size-5 text-amber-500" />
                  Subscription Cancelling
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  Your subscription will end on{" "}
                  <span className="font-semibold text-slate-950 dark:text-slate-50">
                    {formatDateNullable(billing.currentPeriodEnd)}
                  </span>
                  . You will retain full access until then. After that, your account
                  enters a read-only grace period.
                </p>
                <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
                  <Users className="size-4" />
                  {currentMembers} of {maxMembers} members
                </div>
                {billing.canSubscribe && (
                  <div>
                    <p className="mb-2 text-sm font-medium text-slate-700 dark:text-slate-300">
                      Changed your mind? Resubscribe to keep your account active.
                    </p>
                    <SubscribeButton />
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {/* PAST_DUE */}
          {billing.status === "PAST_DUE" && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <AlertTriangle className="size-5 text-amber-500" />
                  Payment Failed
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <p className="text-sm text-slate-600 dark:text-slate-400">
                  Your latest payment failed. Please update your payment method
                  through PayFast or subscribe again to maintain access.
                </p>
                <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
                  <Users className="size-4" />
                  {currentMembers} of {maxMembers} members
                </div>
                {billing.canSubscribe && <SubscribeButton />}
              </CardContent>
            </Card>
          )}

          {/* GRACE_PERIOD / EXPIRED */}
          {(billing.status === "GRACE_PERIOD" ||
            billing.status === "EXPIRED") && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <XCircle className="size-5 text-red-500" />
                  {billing.status === "GRACE_PERIOD"
                    ? "Grace Period"
                    : "Subscription Expired"}
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                {billing.graceEndsAt && (
                  <GraceCountdown graceEndsAt={billing.graceEndsAt} />
                )}
                <div className="rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
                  Your account is in read-only mode.{" "}
                  {billing.graceEndsAt && (
                    <>
                      Grace period ends {formatDate(billing.graceEndsAt)}.
                    </>
                  )}
                </div>
                <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
                  <Users className="size-4" />
                  {currentMembers} of {maxMembers} members
                </div>
                <p className="text-lg font-semibold text-slate-950 dark:text-slate-50">
                  {amount}
                  <span className="text-sm font-normal text-slate-500">
                    /month
                  </span>
                </p>
                {billing.canSubscribe && <SubscribeButton />}
              </CardContent>
            </Card>
          )}

          {/* SUSPENDED */}
          {billing.status === "SUSPENDED" && (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <XCircle className="size-5 text-red-500" />
                  Account Suspended
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
                  Your account has been suspended.{" "}
                  {billing.graceEndsAt && (
                    <>
                      Grace period ends {formatDate(billing.graceEndsAt)} (
                      {daysRemaining(billing.graceEndsAt)} days remaining).
                    </>
                  )}
                </div>
                <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
                  <Users className="size-4" />
                  {currentMembers} of {maxMembers} members
                </div>
                {billing.canSubscribe && <SubscribeButton />}
              </CardContent>
            </Card>
          )}

          {/* LOCKED */}
          {billing.status === "LOCKED" && (
            <div className="flex min-h-[50vh] items-center justify-center">
              <Card className="max-w-md text-center">
                <CardHeader>
                  <CardTitle className="flex items-center justify-center gap-2">
                    <Lock className="size-5 text-red-500" />
                    Account Locked
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <p className="text-sm text-slate-600 dark:text-slate-400">
                    Your account has been locked due to an expired subscription.
                    Your data is preserved and will be accessible once you
                    resubscribe.
                  </p>
                  <p className="text-lg font-semibold text-slate-950 dark:text-slate-50">
                    {amount}
                    <span className="text-sm font-normal text-slate-500">
                      /month
                    </span>
                  </p>
                  {billing.canSubscribe && <SubscribeButton />}
                </CardContent>
              </Card>
            </div>
          )}

          {/* Payment History */}
          {billing.status !== "LOCKED" && (
            <Card>
              <CardHeader>
                <CardTitle>Payment History</CardTitle>
              </CardHeader>
              <CardContent>
                <Suspense
                  fallback={
                    <p className="text-sm text-slate-500">Loading payments...</p>
                  }
                >
                  <PaymentHistory />
                </Suspense>
              </CardContent>
            </Card>
          )}
        </>
      )}
    </div>
  );
}
