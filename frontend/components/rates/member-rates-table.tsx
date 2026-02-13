"use client";

import { useMemo, useState } from "react";
import { Pencil, Plus, Trash2, Users } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { EmptyState } from "@/components/empty-state";
import { CurrencySelector } from "@/components/rates/currency-selector";
import { AddRateDialog } from "@/components/rates/add-rate-dialog";
import { EditRateDialog } from "@/components/rates/edit-rate-dialog";
import { DeleteRateDialog } from "@/components/rates/delete-rate-dialog";
import { updateDefaultCurrency } from "@/app/(app)/org/[slug]/settings/rates/actions";
import { formatCurrency, formatDate } from "@/lib/format";
import type { OrgMember, BillingRate, CostRate } from "@/lib/types";

function getActiveRate<T extends { effectiveFrom: string; effectiveTo: string | null }>(
  rates: T[],
): T | undefined {
  const today = new Date().toLocaleDateString("en-CA");
  return rates.find((r) => {
    if (r.effectiveFrom > today) return false;
    if (r.effectiveTo && r.effectiveTo < today) return false;
    return true;
  });
}

interface MemberRatesTableProps {
  slug: string;
  members: OrgMember[];
  billingRates: BillingRate[];
  costRates: CostRate[];
  defaultCurrency: string;
}

export function MemberRatesTable({
  slug,
  members,
  billingRates,
  costRates,
  defaultCurrency,
}: MemberRatesTableProps) {
  const [currency, setCurrency] = useState(defaultCurrency);
  const [isSavingCurrency, setIsSavingCurrency] = useState(false);
  const [currencyMessage, setCurrencyMessage] = useState<string | null>(null);

  const billingRatesByMember = useMemo(() => {
    const map = new Map<string, BillingRate[]>();
    for (const rate of billingRates) {
      const existing = map.get(rate.memberId) ?? [];
      existing.push(rate);
      map.set(rate.memberId, existing);
    }
    return map;
  }, [billingRates]);

  const costRatesByMember = useMemo(() => {
    const map = new Map<string, CostRate[]>();
    for (const rate of costRates) {
      const existing = map.get(rate.memberId) ?? [];
      existing.push(rate);
      map.set(rate.memberId, existing);
    }
    return map;
  }, [costRates]);

  async function handleCurrencyChange(newCurrency: string) {
    setCurrency(newCurrency);
    setCurrencyMessage(null);
    setIsSavingCurrency(true);

    try {
      const result = await updateDefaultCurrency(slug, newCurrency);
      if (result.success) {
        setCurrencyMessage("Default currency updated.");
      } else {
        setCurrencyMessage(result.error ?? "Failed to update currency.");
        setCurrency(defaultCurrency);
      }
    } catch {
      setCurrencyMessage("An unexpected error occurred.");
      setCurrency(defaultCurrency);
    } finally {
      setIsSavingCurrency(false);
    }
  }

  if (members.length === 0) {
    return (
      <EmptyState
        icon={Users}
        title="No members found"
        description="Organization members will appear here."
      />
    );
  }

  return (
    <div className="space-y-8">
      {/* Currency selector */}
      <div className="rounded-lg border border-olive-200 bg-white p-6 dark:border-olive-800 dark:bg-olive-950">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-olive-950 dark:text-olive-50">
              Default Currency
            </h2>
            <p className="mt-1 text-sm text-olive-600 dark:text-olive-400">
              This currency will be pre-selected when creating new rates.
            </p>
          </div>
          <CurrencySelector
            value={currency}
            onChange={handleCurrencyChange}
            disabled={isSavingCurrency}
          />
        </div>
        {currencyMessage && (
          <p
            className={`mt-2 text-sm ${
              currencyMessage.includes("updated")
                ? "text-green-600 dark:text-green-400"
                : "text-red-600 dark:text-red-400"
            }`}
          >
            {currencyMessage}
          </p>
        )}
      </div>

      {/* Rates tabs */}
      <Tabs defaultValue="billing">
        <TabsList>
          <TabsTrigger value="billing">Billing Rates</TabsTrigger>
          <TabsTrigger value="cost">Cost Rates</TabsTrigger>
        </TabsList>

        <TabsContent value="billing" className="mt-4">
          <div className="rounded-lg border border-olive-200 bg-white dark:border-olive-800 dark:bg-olive-950">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Member</TableHead>
                  <TableHead>Hourly Rate</TableHead>
                  <TableHead>Currency</TableHead>
                  <TableHead>Effective From</TableHead>
                  <TableHead>Effective To</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {members.map((member) => {
                  const memberRates = billingRatesByMember.get(member.id) ?? [];
                  const activeRate = getActiveRate(memberRates);

                  return (
                    <TableRow key={member.id}>
                      <TableCell>
                        <div className="flex items-center gap-3">
                          <AvatarCircle name={member.name} size={32} />
                          <div className="min-w-0">
                            <p className="truncate font-medium text-olive-900 dark:text-olive-100">
                              {member.name}
                            </p>
                            <p className="truncate text-xs text-olive-500 dark:text-olive-400">
                              {member.email}
                            </p>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        {activeRate ? (
                          <span className="font-medium">
                            {formatCurrency(
                              activeRate.hourlyRate,
                              activeRate.currency,
                            )}
                          </span>
                        ) : (
                          <span className="text-olive-400 dark:text-olive-600">
                            Not set
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        {activeRate ? (
                          activeRate.currency
                        ) : (
                          <span className="text-olive-400 dark:text-olive-600">
                            —
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        {activeRate ? (
                          formatDate(activeRate.effectiveFrom)
                        ) : (
                          <span className="text-olive-400 dark:text-olive-600">
                            —
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        {activeRate?.effectiveTo ? (
                          formatDate(activeRate.effectiveTo)
                        ) : activeRate ? (
                          <span className="text-olive-400 dark:text-olive-600">
                            Ongoing
                          </span>
                        ) : (
                          <span className="text-olive-400 dark:text-olive-600">
                            —
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-1">
                          {activeRate ? (
                            <>
                              <EditRateDialog
                                slug={slug}
                                rate={activeRate}
                                rateType="billing"
                              >
                                <Button
                                  variant="plain"
                                  size="sm"
                                  aria-label={`Edit billing rate for ${member.name}`}
                                >
                                  <Pencil className="size-4" />
                                </Button>
                              </EditRateDialog>
                              <DeleteRateDialog
                                slug={slug}
                                rateId={activeRate.id}
                                rateType="billing"
                                memberName={member.name}
                              >
                                <Button
                                  variant="plain"
                                  size="sm"
                                  aria-label={`Delete billing rate for ${member.name}`}
                                >
                                  <Trash2 className="size-4 text-red-500" />
                                </Button>
                              </DeleteRateDialog>
                            </>
                          ) : (
                            <AddRateDialog
                              slug={slug}
                              member={member}
                              defaultCurrency={currency}
                            >
                              <Button
                                variant="plain"
                                size="sm"
                                aria-label={`Add rate for ${member.name}`}
                              >
                                <Plus className="size-4" />
                                <span className="ml-1">Add Rate</span>
                              </Button>
                            </AddRateDialog>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
        </TabsContent>

        <TabsContent value="cost" className="mt-4">
          <div className="rounded-lg border border-olive-200 bg-white dark:border-olive-800 dark:bg-olive-950">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Member</TableHead>
                  <TableHead>Hourly Cost</TableHead>
                  <TableHead>Currency</TableHead>
                  <TableHead>Effective From</TableHead>
                  <TableHead>Effective To</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {members.map((member) => {
                  const memberRates = costRatesByMember.get(member.id) ?? [];
                  const activeRate = getActiveRate(memberRates);

                  return (
                    <TableRow key={member.id}>
                      <TableCell>
                        <div className="flex items-center gap-3">
                          <AvatarCircle name={member.name} size={32} />
                          <div className="min-w-0">
                            <p className="truncate font-medium text-olive-900 dark:text-olive-100">
                              {member.name}
                            </p>
                            <p className="truncate text-xs text-olive-500 dark:text-olive-400">
                              {member.email}
                            </p>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>
                        {activeRate ? (
                          <span className="font-medium">
                            {formatCurrency(
                              activeRate.hourlyCost,
                              activeRate.currency,
                            )}
                          </span>
                        ) : (
                          <span className="text-olive-400 dark:text-olive-600">
                            Not set
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        {activeRate ? (
                          activeRate.currency
                        ) : (
                          <span className="text-olive-400 dark:text-olive-600">
                            —
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        {activeRate ? (
                          formatDate(activeRate.effectiveFrom)
                        ) : (
                          <span className="text-olive-400 dark:text-olive-600">
                            —
                          </span>
                        )}
                      </TableCell>
                      <TableCell>
                        {activeRate?.effectiveTo ? (
                          formatDate(activeRate.effectiveTo)
                        ) : activeRate ? (
                          <span className="text-olive-400 dark:text-olive-600">
                            Ongoing
                          </span>
                        ) : (
                          <span className="text-olive-400 dark:text-olive-600">
                            —
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-1">
                          {activeRate ? (
                            <>
                              <EditRateDialog
                                slug={slug}
                                rate={activeRate}
                                rateType="cost"
                              >
                                <Button
                                  variant="plain"
                                  size="sm"
                                  aria-label={`Edit cost rate for ${member.name}`}
                                >
                                  <Pencil className="size-4" />
                                </Button>
                              </EditRateDialog>
                              <DeleteRateDialog
                                slug={slug}
                                rateId={activeRate.id}
                                rateType="cost"
                                memberName={member.name}
                              >
                                <Button
                                  variant="plain"
                                  size="sm"
                                  aria-label={`Delete cost rate for ${member.name}`}
                                >
                                  <Trash2 className="size-4 text-red-500" />
                                </Button>
                              </DeleteRateDialog>
                            </>
                          ) : (
                            <AddRateDialog
                              slug={slug}
                              member={member}
                              defaultCurrency={currency}
                            >
                              <Button
                                variant="plain"
                                size="sm"
                                aria-label={`Add rate for ${member.name}`}
                              >
                                <Plus className="size-4" />
                                <span className="ml-1">Add Rate</span>
                              </Button>
                            </AddRateDialog>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}
