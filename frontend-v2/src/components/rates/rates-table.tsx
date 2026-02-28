"use client";

import { useState } from "react";
import { Plus, DollarSign } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { EmptyState } from "@/components/empty-state";
import type { OrgMember, BillingRate, CostRate } from "@/lib/types";

type Rate = BillingRate | CostRate;

interface RatesTableProps {
  slug: string;
  members: OrgMember[];
  rates: Rate[];
  defaultCurrency: string;
  type: "billing" | "cost";
}

export function RatesTable({
  slug,
  members,
  rates,
  defaultCurrency,
  type,
}: RatesTableProps) {
  const [rateList] = useState(rates);

  // Build a map of member ID -> rate
  const rateByMember = new Map<string, Rate>();
  for (const rate of rateList) {
    if ("memberId" in rate && rate.memberId) {
      rateByMember.set(rate.memberId, rate);
    }
  }

  // Default org rate (no member)
  const orgRate = rateList.find(
    (r) => !("memberId" in r && r.memberId),
  );

  const typeLabel = type === "billing" ? "Billing Rate" : "Cost Rate";

  return (
    <div className="space-y-6">
      {/* Org Default */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">
              Organization Default
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              The default {type} rate applied when no member-specific rate is
              set.
            </p>
          </div>
          <div className="text-right">
            {orgRate ? (
              <p className="font-mono text-xl font-semibold text-slate-900">
                {defaultCurrency}{" "}
                {"hourlyRate" in orgRate
                  ? (orgRate as BillingRate).hourlyRate
                  : "hourlyCost" in orgRate
                    ? (orgRate as CostRate).hourlyCost
                    : "0.00"}
                <span className="text-sm font-normal text-slate-500">
                  /hr
                </span>
              </p>
            ) : (
              <p className="text-sm text-slate-500">Not set</p>
            )}
          </div>
        </div>
      </div>

      {/* Member Rates */}
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">
              Member {typeLabel}s
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              Override the default rate for specific team members.
            </p>
          </div>
          <Button size="sm" variant="outline">
            <Plus className="mr-1.5 size-4" />
            Add rate
          </Button>
        </div>

        {members.length === 0 ? (
          <div className="mt-6">
            <EmptyState
              icon={DollarSign}
              title={`No ${type} rates`}
              description={`Add member-specific ${type} rates to track profitability.`}
            />
          </div>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200">
                  <th className="pb-3 pr-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                    Member
                  </th>
                  <th className="w-[150px] pb-3 pr-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                    Rate ({defaultCurrency}/hr)
                  </th>
                  <th className="w-[100px] pb-3 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                    Status
                  </th>
                </tr>
              </thead>
              <tbody>
                {members.map((member) => {
                  const rate = rateByMember.get(member.id);
                  return (
                    <tr
                      key={member.id}
                      className="border-b border-slate-100 last:border-0"
                    >
                      <td className="py-3 pr-4">
                        <div className="flex items-center gap-3">
                          <AvatarCircle
                            name={member.name ?? "Unknown"}
                            size={28}
                          />
                          <span className="font-medium text-slate-900">
                            {member.name ?? "Unknown"}
                          </span>
                        </div>
                      </td>
                      <td className="py-3 pr-4 font-mono text-slate-700">
                        {rate
                          ? "hourlyRate" in rate
                            ? (rate as BillingRate).hourlyRate
                            : "hourlyCost" in rate
                              ? (rate as CostRate).hourlyCost
                              : "--"
                          : "--"}
                      </td>
                      <td className="py-3 text-sm text-slate-500">
                        {rate ? "Custom" : "Default"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
