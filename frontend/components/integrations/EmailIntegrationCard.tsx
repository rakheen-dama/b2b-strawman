"use client";

import { useEffect, useState } from "react";
import { Mail, Send, ChevronDown, ChevronUp } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { getEmailStats, sendTestEmail } from "@/lib/actions/email";
import type { EmailDeliveryStats } from "@/lib/api/email";

interface EmailIntegrationCardProps {
  slug: string;
}

export function EmailIntegrationCard({ slug }: EmailIntegrationCardProps) {
  const [stats, setStats] = useState<EmailDeliveryStats | null>(null);
  const [isLoadingStats, setIsLoadingStats] = useState(true);
  const [isSendingTest, setIsSendingTest] = useState(false);
  const [testResult, setTestResult] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Suppress unused variable warning — slug reserved for future BYOAK config
  void slug;

  useEffect(() => {
    async function loadStats() {
      setIsLoadingStats(true);
      const result = await getEmailStats();
      if (result.success && result.data) {
        setStats(result.data);
      } else {
        setError(result.error ?? "Failed to load email stats.");
      }
      setIsLoadingStats(false);
    }
    loadStats();
  }, []);

  async function handleSendTest() {
    setIsSendingTest(true);
    setTestResult(null);
    setError(null);
    const result = await sendTestEmail();
    if (result.success) {
      setTestResult("Test email sent successfully.");
    } else {
      setError(result.error ?? "Failed to send test email.");
    }
    setIsSendingTest(false);
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-lg bg-slate-100 dark:bg-slate-800">
              <Mail className="size-5 text-slate-600 dark:text-slate-400" />
            </div>
            <CardTitle className="font-display text-lg">
              Email Delivery
            </CardTitle>
          </div>
          <Badge variant="success">Active</Badge>
        </div>
        <CardDescription>
          Platform email delivery with optional BYOAK configuration
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {error && (
          <p className="text-sm text-destructive" role="alert">
            {error}
          </p>
        )}

        {/* Platform email info */}
        <div className="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2 dark:bg-slate-900">
          <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
            Platform Email
          </span>
          <Badge variant="success">Active</Badge>
        </div>

        {/* Stats summary */}
        {isLoadingStats ? (
          <p className="text-sm text-slate-500">Loading stats...</p>
        ) : stats ? (
          <div className="grid grid-cols-3 gap-3">
            <div className="rounded-md border border-slate-200 px-3 py-2 dark:border-slate-800">
              <p className="font-mono text-lg font-semibold text-slate-900 dark:text-slate-100">
                {stats.sent24h}
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Sent (24h)
              </p>
            </div>
            <div className="rounded-md border border-slate-200 px-3 py-2 dark:border-slate-800">
              <p className="font-mono text-lg font-semibold text-slate-900 dark:text-slate-100">
                {stats.bounced7d}
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Bounced (7d)
              </p>
            </div>
            <div className="rounded-md border border-slate-200 px-3 py-2 dark:border-slate-800">
              <p className="font-mono text-lg font-semibold text-slate-900 dark:text-slate-100">
                {stats.currentHourUsage}/{stats.hourlyLimit}
              </p>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Rate Limit
              </p>
            </div>
          </div>
        ) : null}

        {/* Provider info */}
        {stats?.providerSlug && (
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Provider: {stats.providerSlug}
          </p>
        )}

        {/* Send test email button */}
        <div className="flex items-center gap-3">
          <Button
            variant="outline"
            size="sm"
            onClick={handleSendTest}
            disabled={isSendingTest}
          >
            <Send className="mr-1.5 size-4" />
            {isSendingTest ? "Sending..." : "Send Test Email"}
          </Button>
          {testResult && (
            <p className="text-sm text-green-600 dark:text-green-400">
              {testResult}
            </p>
          )}
        </div>

        {/* Expandable BYOAK section */}
        <div className="border-t border-slate-200 pt-3 dark:border-slate-800">
          <button
            type="button"
            className="flex w-full items-center justify-between text-sm font-medium text-slate-700 dark:text-slate-300"
            onClick={() => setExpanded(!expanded)}
          >
            <span>Bring Your Own API Key (SendGrid)</span>
            {expanded ? (
              <ChevronUp className="size-4" />
            ) : (
              <ChevronDown className="size-4" />
            )}
          </button>
          {expanded && (
            <div className="mt-3 rounded-md bg-slate-50 px-3 py-3 dark:bg-slate-900">
              <p className="text-sm text-slate-500 dark:text-slate-400">
                BYOAK configuration coming soon. You will be able to connect
                your own SendGrid API key for custom email delivery.
              </p>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
