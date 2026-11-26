"use client";

import { useEffect, useState } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { getEmailStats } from "@/lib/actions/email";
import type { EmailDeliveryStats } from "@/lib/api/email";
import { SettingsSection } from "@/components/settings/settings-section";
import { DeliveryLogTable } from "./DeliveryLogTable";

function StatCard({ value, label }: { value: string | number; label: string }) {
  return (
    <div className="rounded-md border px-3 py-2">
      <p className="text-foreground font-mono text-lg font-semibold">{value}</p>
      <p className="text-muted-foreground text-xs">{label}</p>
    </div>
  );
}

function OverviewTab() {
  const [stats, setStats] = useState<EmailDeliveryStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      const result = await getEmailStats();
      if (cancelled) return;
      if (result.success && result.data) {
        setStats(result.data);
      } else {
        setError(result.error ?? "Failed to load email stats.");
      }
      setIsLoading(false);
    }
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  if (isLoading) {
    return <p className="text-muted-foreground text-sm">Loading stats...</p>;
  }

  if (error) {
    return <p className="text-destructive text-sm">{error}</p>;
  }

  if (!stats) {
    return null;
  }

  return (
    <SettingsSection title="Delivery Statistics" as="h3">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        <StatCard value={stats.sent24h} label="Sent (24h)" />
        <StatCard value={stats.bounced7d} label="Bounced (7d)" />
        <StatCard value={stats.failed7d} label="Failed (7d)" />
        <StatCard value={stats.rateLimited7d} label="Rate Limited (7d)" />
        <StatCard
          value={`${stats.currentHourUsage}/${stats.hourlyLimit}`}
          label="Current Hour Usage"
        />
        <StatCard value={stats.providerSlug ?? "Platform"} label="Provider" />
      </div>
    </SettingsSection>
  );
}

type TabValue = "overview" | "delivery-log";

export function EmailSettingsContent() {
  const [activeTab, setActiveTab] = useState<TabValue>("overview");

  return (
    <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as TabValue)}>
      <TabsList variant="line">
        <TabsTrigger value="overview">Overview</TabsTrigger>
        <TabsTrigger value="delivery-log">Delivery Log</TabsTrigger>
      </TabsList>

      <TabsContent value="overview" className="space-y-6">
        <OverviewTab />
      </TabsContent>

      <TabsContent value="delivery-log" className="space-y-6">
        <DeliveryLogTable />
      </TabsContent>
    </Tabs>
  );
}
