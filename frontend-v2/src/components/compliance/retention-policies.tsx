"use client";

import { Shield, Plus } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";
import type { RetentionPolicy } from "@/lib/types";

interface RetentionPoliciesProps {
  slug: string;
  policies: RetentionPolicy[];
}

export function RetentionPolicies({
  slug,
  policies,
}: RetentionPoliciesProps) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">
            Retention Policies
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Define how long different types of data are retained.
          </p>
        </div>
        <Button size="sm" variant="outline">
          <Plus className="mr-1.5 size-4" />
          Add policy
        </Button>
      </div>

      {policies.length === 0 ? (
        <div className="mt-4">
          <EmptyState
            icon={Shield}
            title="No retention policies"
            description="Add retention policies to define data lifecycle rules."
          />
        </div>
      ) : (
        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200">
                <th className="pb-3 pr-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                  Entity Type
                </th>
                <th className="pb-3 pr-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                  Retention Period
                </th>
                <th className="pb-3 pr-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                  Action
                </th>
                <th className="pb-3 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                  Status
                </th>
              </tr>
            </thead>
            <tbody>
              {policies.map((policy) => (
                <tr
                  key={policy.id}
                  className="border-b border-slate-100 last:border-0"
                >
                  <td className="py-3 pr-4 font-medium text-slate-900">
                    {policy.recordType}
                  </td>
                  <td className="py-3 pr-4 text-slate-700">
                    {policy.retentionDays} days
                  </td>
                  <td className="py-3 pr-4 text-slate-700">
                    {policy.action ?? "Archive"}
                  </td>
                  <td className="py-3">
                    <Badge
                      variant={policy.active ? "success" : "neutral"}
                    >
                      {policy.active ? "Active" : "Inactive"}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
