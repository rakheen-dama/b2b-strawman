"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

interface ComplianceSettingsProps {
  slug: string;
  dormancyThresholdDays: number;
  dataRequestDeadlineDays: number;
}

export function ComplianceSettings({
  slug,
  dormancyThresholdDays: initialDormancy,
  dataRequestDeadlineDays: initialDeadline,
}: ComplianceSettingsProps) {
  const [dormancyDays, setDormancyDays] = useState(initialDormancy);
  const [deadlineDays, setDeadlineDays] = useState(initialDeadline);
  const [isSaving, setIsSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function handleSave() {
    setIsSaving(true);
    setMessage(null);
    try {
      const res = await fetch("/api/settings", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          dormancyThresholdDays: dormancyDays,
          dataRequestDeadlineDays: deadlineDays,
        }),
      });
      if (res.ok) {
        setMessage("Settings saved.");
      } else {
        setMessage("Failed to save settings.");
      }
    } catch {
      setMessage("Failed to save settings.");
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-slate-900">
        General Compliance Settings
      </h2>
      <p className="mt-1 text-sm text-slate-500">
        Configure dormancy thresholds and data request deadlines.
      </p>

      <div className="mt-6 grid gap-6 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="dormancy-days">
            Dormancy Threshold (days)
          </Label>
          <Input
            id="dormancy-days"
            type="number"
            min={30}
            value={dormancyDays}
            onChange={(e) => setDormancyDays(Number(e.target.value))}
          />
          <p className="text-xs text-slate-400">
            Customers with no activity for this many days are flagged as
            dormant.
          </p>
        </div>

        <div className="space-y-2">
          <Label htmlFor="deadline-days">
            Data Request Deadline (days)
          </Label>
          <Input
            id="deadline-days"
            type="number"
            min={1}
            value={deadlineDays}
            onChange={(e) => setDeadlineDays(Number(e.target.value))}
          />
          <p className="text-xs text-slate-400">
            Default deadline for completing data access/deletion requests.
          </p>
        </div>
      </div>

      <div className="mt-6 flex items-center gap-4">
        <Button onClick={handleSave} disabled={isSaving} size="sm">
          {isSaving ? "Saving..." : "Save settings"}
        </Button>
        {message && (
          <p
            className={`text-sm ${
              message.includes("saved")
                ? "text-emerald-600"
                : "text-red-600"
            }`}
          >
            {message}
          </p>
        )}
      </div>
    </div>
  );
}
