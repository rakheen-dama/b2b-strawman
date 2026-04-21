"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Bell, CheckCircle2 } from "lucide-react";
import { useAuth } from "@/hooks/use-auth";
import {
  getPreferences,
  updatePreferences,
  type NotificationPreferences,
  type NotificationPreferencesUpdate,
} from "@/lib/api/notification-preferences";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

interface ToggleRowProps {
  id: string;
  label: string;
  description: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (next: boolean) => void;
}

function ToggleRow({
  id,
  label,
  description,
  checked,
  disabled,
  onChange,
}: ToggleRowProps) {
  return (
    <label
      htmlFor={id}
      data-testid={`toggle-row-${id}`}
      className="flex min-h-11 w-full cursor-pointer items-start justify-between gap-4 py-3"
    >
      <span className="flex-1">
        <span className="block text-sm font-medium text-slate-900">
          {label}
        </span>
        <span className="mt-1 block text-xs text-slate-500">{description}</span>
      </span>
      <input
        id={id}
        type="checkbox"
        role="switch"
        aria-checked={checked}
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
        className="mt-1 size-5 rounded accent-teal-600 disabled:opacity-50"
      />
    </label>
  );
}

function NotificationsSkeleton() {
  return (
    <Card>
      <CardHeader>
        <Skeleton className="h-6 w-48" />
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      </CardContent>
    </Card>
  );
}

function cadenceLabel(cadence: NotificationPreferences["firmDigestCadence"]) {
  switch (cadence) {
    case "WEEKLY":
      return "Your firm sends weekly digests.";
    case "BIWEEKLY":
      return "Your firm sends bi-weekly digests.";
    case "OFF":
      return "Your firm currently has digests turned off.";
    default:
      return "";
  }
}

function NotificationsPageInner() {
  const { jwt } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const shouldAutoUnsubscribe = searchParams.get("unsubscribe") === "1";

  const [prefs, setPrefs] = useState<NotificationPreferences | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [autoUnsubscribed, setAutoUnsubscribed] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  // Gate the auth-redirect branch on post-hydration mount. `useAuth` reads JWT
  // from localStorage, which is null on the very first client render — without
  // this guard we would bounce authenticated users to /login during hydration.
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (!mounted) return;
    if (!jwt) {
      router.push("/login");
      return;
    }

    let cancelled = false;
    async function fetchPrefs() {
      try {
        const data = await getPreferences();
        if (!cancelled) {
          setPrefs(data);
        }
      } catch (err) {
        if (!cancelled) {
          const message =
            err instanceof Error ? err.message : "Failed to load preferences";
          if (message === "Unauthorized") {
            router.push("/login");
          } else {
            setError(message);
          }
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }
    fetchPrefs();
    return () => {
      cancelled = true;
    };
  }, [jwt, router, mounted]);

  const save = useCallback(
    async (next: NotificationPreferencesUpdate): Promise<void> => {
      setIsSaving(true);
      setError(null);
      try {
        const saved = await updatePreferences(next);
        setPrefs(saved);
      } catch (err) {
        const message =
          err instanceof Error ? err.message : "Failed to save preferences";
        setError(message);
        throw err;
      } finally {
        setIsSaving(false);
      }
    },
    [],
  );

  // 498.18 — auto-unsubscribe landing: ?unsubscribe=1 flips digest off once, shows banner.
  // After the handler runs we strip the query param via router.replace so that a page
  // refresh (or the user re-enabling digest then reloading) doesn't re-fire the effect
  // and silently flip digest back off.
  useEffect(() => {
    if (!shouldAutoUnsubscribe || autoUnsubscribed || !prefs) return;
    if (!prefs.digestEnabled) {
      // already off — still surface the banner so the link feels honoured
      setAutoUnsubscribed(true);
      router.replace("/settings/notifications");
      return;
    }
    const next: NotificationPreferencesUpdate = {
      digestEnabled: false,
      trustActivityEnabled: prefs.trustActivityEnabled,
      retainerUpdatesEnabled: prefs.retainerUpdatesEnabled,
      deadlineRemindersEnabled: prefs.deadlineRemindersEnabled,
      actionRequiredEnabled: prefs.actionRequiredEnabled,
    };
    save(next)
      .then(() => {
        setAutoUnsubscribed(true);
        router.replace("/settings/notifications");
      })
      .catch(() => {
        /* error state is set by save() — banner stays hidden, query param preserved */
      });
  }, [shouldAutoUnsubscribe, autoUnsubscribed, prefs, save, router]);

  async function handleToggle(
    field: keyof NotificationPreferencesUpdate,
    value: boolean,
  ) {
    if (!prefs) return;
    const next: NotificationPreferencesUpdate = {
      digestEnabled: prefs.digestEnabled,
      trustActivityEnabled: prefs.trustActivityEnabled,
      retainerUpdatesEnabled: prefs.retainerUpdatesEnabled,
      deadlineRemindersEnabled: prefs.deadlineRemindersEnabled,
      actionRequiredEnabled: prefs.actionRequiredEnabled,
      [field]: value,
    };
    try {
      await save(next);
      setSuccessMessage("Preferences saved.");
    } catch {
      /* error already surfaced */
    }
  }

  async function handleUnsubscribeAll() {
    if (!prefs) return;
    const next: NotificationPreferencesUpdate = {
      digestEnabled: false,
      trustActivityEnabled: false,
      retainerUpdatesEnabled: false,
      deadlineRemindersEnabled: false,
      actionRequiredEnabled: false,
    };
    try {
      await save(next);
      setSuccessMessage("You have been unsubscribed from all notifications.");
    } catch {
      /* error already surfaced */
    }
  }

  return (
    <div>
      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        Notification Preferences
      </h1>

      {autoUnsubscribed && (
        <div
          role="status"
          className="mb-6 flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800"
        >
          <CheckCircle2 className="mt-0.5 size-4 shrink-0" />
          <div>
            <p className="font-medium">
              You&apos;ve been unsubscribed from weekly digests.
            </p>
            <p className="mt-1 text-emerald-700">
              You can re-enable any channel below at any time.
            </p>
          </div>
        </div>
      )}

      {isLoading && <NotificationsSkeleton />}

      {error && !isLoading && (
        <div
          role="alert"
          className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700"
        >
          {error}
        </div>
      )}

      {!isLoading && !error && prefs && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Bell className="size-5 text-slate-500" />
                Email notifications
              </CardTitle>
            </CardHeader>
            <CardContent>
              <p className="mb-2 text-xs text-slate-500">
                {cadenceLabel(prefs.firmDigestCadence)}
              </p>
              <div className="divide-y divide-slate-100">
                <ToggleRow
                  id="pref-digest"
                  label="Weekly digest"
                  description="Your periodic summary of account activity."
                  checked={prefs.digestEnabled}
                  disabled={isSaving}
                  onChange={(v) => handleToggle("digestEnabled", v)}
                />
                <ToggleRow
                  id="pref-trust"
                  label="Trust activity"
                  description="Deposits, disbursements, and balance changes on your trust account."
                  checked={prefs.trustActivityEnabled}
                  disabled={isSaving}
                  onChange={(v) => handleToggle("trustActivityEnabled", v)}
                />
                <ToggleRow
                  id="pref-retainer"
                  label="Retainer updates"
                  description="Hour usage, thresholds, and top-up reminders on your retainers."
                  checked={prefs.retainerUpdatesEnabled}
                  disabled={isSaving}
                  onChange={(v) => handleToggle("retainerUpdatesEnabled", v)}
                />
                <ToggleRow
                  id="pref-deadline"
                  label="Deadline reminders"
                  description="Upcoming deadlines and due-date alerts."
                  checked={prefs.deadlineRemindersEnabled}
                  disabled={isSaving}
                  onChange={(v) => handleToggle("deadlineRemindersEnabled", v)}
                />
                <ToggleRow
                  id="pref-action"
                  label="Action-required notifications"
                  description="Invoice, proposal, acceptance, and information-request emails."
                  checked={prefs.actionRequiredEnabled}
                  disabled={isSaving}
                  onChange={(v) => handleToggle("actionRequiredEnabled", v)}
                />
              </div>
              {successMessage && (
                <p className="mt-4 text-xs text-emerald-700">
                  {successMessage}
                </p>
              )}
            </CardContent>
          </Card>

          <div className="flex justify-end">
            <Button
              variant="ghost"
              disabled={isSaving}
              onClick={handleUnsubscribeAll}
            >
              Unsubscribe all
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

export default function NotificationsPage() {
  return (
    <Suspense
      fallback={
        <div>
          <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
            Notification Preferences
          </h1>
          <NotificationsSkeleton />
        </div>
      }
    >
      <NotificationsPageInner />
    </Suspense>
  );
}
