"use client";

import { useState } from "react";
import { Plus, Pencil, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import type { TaxRateResponse } from "@/lib/types";

interface TaxRateTableProps {
  slug: string;
  taxRates: TaxRateResponse[];
}

export function TaxRateTable({ slug, taxRates }: TaxRateTableProps) {
  const [rates] = useState(taxRates);

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">Tax Rates</h2>
          <p className="mt-1 text-sm text-slate-500">
            Define tax rates that can be applied to invoices.
          </p>
        </div>
        <Button size="sm" variant="outline">
          <Plus className="mr-1.5 size-4" />
          Add rate
        </Button>
      </div>

      {rates.length === 0 ? (
        <p className="mt-6 text-center text-sm text-slate-500">
          No tax rates configured. Add one to get started.
        </p>
      ) : (
        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200">
                <th className="pb-3 pr-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                  Name
                </th>
                <th className="pb-3 pr-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                  Rate
                </th>
                <th className="pb-3 pr-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                  Status
                </th>
                <th className="pb-3 text-right text-xs font-medium uppercase tracking-wide text-slate-500">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {rates.map((rate) => (
                <tr
                  key={rate.id}
                  className="border-b border-slate-100 last:border-0"
                >
                  <td className="py-3 pr-4 font-medium text-slate-900">
                    {rate.name}
                  </td>
                  <td className="py-3 pr-4 font-mono text-slate-700">
                    {rate.rate}%
                  </td>
                  <td className="py-3 pr-4">
                    <Badge variant={rate.active ? "success" : "neutral"}>
                      {rate.active ? "Active" : "Inactive"}
                    </Badge>
                  </td>
                  <td className="py-3 text-right">
                    <div className="flex justify-end gap-1">
                      <Button variant="ghost" size="sm">
                        <Pencil className="size-3.5" />
                      </Button>
                      <Button variant="ghost" size="sm">
                        <Trash2 className="size-3.5 text-red-500" />
                      </Button>
                    </div>
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
