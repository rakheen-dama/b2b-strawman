"use client";

import { useMemo, useState } from "react";
import { DollarSign, Pencil, Plus, Trash2 } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { EmptyState } from "@/components/empty-state";
import { SettingsSection } from "@/components/settings/settings-section";
import { CurrencySelector } from "@/components/rates/currency-selector";
import { AddRateDialog } from "@/components/rates/add-rate-dialog";
import { EditRateDialog } from "@/components/rates/edit-rate-dialog";
import { DeleteRateDialog } from "@/components/rates/delete-rate-dialog";
import { HelpTip } from "@/components/help-tip";
import { updateDefaultCurrency } from "@/app/(app)/org/[slug]/settings/fees/_tabs/tariffs-actions";
import { createMessages } from "@/lib/messages";
import { formatCurrency, formatDate } from "@/lib/format";
import type { OrgMember, BillingRate, CostRate } from "@/lib/types";

function getActiveRate<T extends { effectiveFrom: string; effectiveTo: string | null }>(
  rates: T[]
): T | undefined {
  const today = new Date().toLocaleDateString("en-CA");
  return rates.find((r) => {
    if (r.effectiveFrom > today) return false;
    if (r.effectiveTo && r.effectiveTo < today) return false;
    return true;
  });
}

type RateKind = "billing" | "cost";

/**
 * One table row: a member, that member's active rate of this kind, and the currency "Add Rate"
 * pre-selects (the selector's current value, carried in the row so the memoised columns read it).
 */
type MemberRateRow = { member: OrgMember; currency: string } & (
  | { kind: "billing"; activeRate: BillingRate | undefined }
  | { kind: "cost"; activeRate: CostRate | undefined }
);

const getMemberRowId = (row: MemberRateRow) => row.member.id;

/** `${kind}-rate-${role}` — the row testid e2e keys on (`billing-rate-owner`, `cost-rate-admin`). */
const getMemberRowTestId = (row: MemberRateRow) =>
  `${row.kind}-rate-${row.member.orgRole?.toLowerCase() ?? row.member.id}`;

function rateAmount(row: MemberRateRow): number | undefined {
  if (row.kind === "billing") return row.activeRate?.hourlyRate;
  return row.activeRate?.hourlyCost;
}

/** The Edit trigger for the row's active rate (EditRateDialog's props are a union per rate type). */
function EditRateTrigger({ slug, row }: { slug: string; row: MemberRateRow }) {
  const button = (
    <Button variant="plain" size="sm" aria-label={`Edit ${row.kind} rate for ${row.member.name}`}>
      <Pencil className="size-4" />
    </Button>
  );
  if (row.kind === "billing") {
    return row.activeRate ? (
      <EditRateDialog slug={slug} rate={row.activeRate} rateType="billing">
        {button}
      </EditRateDialog>
    ) : null;
  }
  return row.activeRate ? (
    <EditRateDialog slug={slug} rate={row.activeRate} rateType="cost">
      {button}
    </EditRateDialog>
  ) : null;
}

/** The Billing / Cost tables share their columns; `kind` sets the amount header and the dialogs. */
function rateColumns(slug: string, kind: RateKind) {
  return defineColumns<MemberRateRow>([
    {
      id: "member",
      enableHiding: false,
      meta: { label: "Member" },
      cell: ({ row }) => (
        <div className="flex items-center gap-3">
          <AvatarCircle name={row.original.member.name} size={32} />
          <div className="min-w-0">
            <p className="text-foreground truncate font-medium">{row.original.member.name}</p>
            <p className="text-muted-foreground truncate text-xs">{row.original.member.email}</p>
          </div>
        </div>
      ),
    },
    {
      id: "amount",
      meta: { label: kind === "billing" ? "Hourly Rate" : "Hourly Cost" },
      cell: ({ row }) => {
        const rate = row.original.activeRate;
        const amount = rateAmount(row.original);
        return rate && amount !== undefined ? (
          <span className="font-medium">{formatCurrency(amount, rate.currency)}</span>
        ) : (
          <span className="text-muted-foreground">Not set</span>
        );
      },
    },
    {
      id: "currency",
      meta: { label: "Currency" },
      cell: ({ row }) => {
        const rate = row.original.activeRate;
        return rate ? rate.currency : <span className="text-muted-foreground">—</span>;
      },
    },
    {
      id: "effectiveFrom",
      meta: { label: "Effective From" },
      cell: ({ row }) => {
        const rate = row.original.activeRate;
        return rate ? (
          formatDate(rate.effectiveFrom)
        ) : (
          <span className="text-muted-foreground">—</span>
        );
      },
    },
    {
      id: "effectiveTo",
      meta: { label: "Effective To" },
      cell: ({ row }) => {
        const rate = row.original.activeRate;
        return rate?.effectiveTo ? (
          formatDate(rate.effectiveTo)
        ) : rate ? (
          <span className="text-muted-foreground">Ongoing</span>
        ) : (
          <span className="text-muted-foreground">—</span>
        );
      },
    },
    {
      id: "actions",
      enableHiding: false,
      meta: { label: "Actions", align: "end" },
      cell: ({ row }) => {
        const { member, activeRate, currency } = row.original;
        return (
          <div className="flex items-center justify-end gap-1">
            {activeRate ? (
              <>
                <EditRateTrigger slug={slug} row={row.original} />
                <DeleteRateDialog
                  slug={slug}
                  rateId={activeRate.id}
                  rateType={kind}
                  memberName={member.name}
                >
                  <Button
                    variant="plain"
                    size="sm"
                    aria-label={`Delete ${kind} rate for ${member.name}`}
                  >
                    <Trash2 className="text-destructive size-4" />
                  </Button>
                </DeleteRateDialog>
              </>
            ) : (
              <AddRateDialog
                slug={slug}
                member={member}
                defaultCurrency={currency}
                defaultRateType={kind}
              >
                <Button variant="plain" size="sm" aria-label={`Add rate for ${member.name}`}>
                  <Plus className="size-4" />
                  <span className="ml-1">Add Rate</span>
                </Button>
              </AddRateDialog>
            )}
          </div>
        );
      },
    },
  ]);
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

  // Stable columns: the rate dialogs own their open state, and a rebuilt `cell` would remount them.
  // The per-row values (active rate, the currency "Add Rate" pre-selects) travel in the rows.
  const billingColumns = useMemo(() => rateColumns(slug, "billing"), [slug]);
  const costColumns = useMemo(() => rateColumns(slug, "cost"), [slug]);
  const billingRows = useMemo<MemberRateRow[]>(
    () =>
      (members ?? []).map((member) => ({
        kind: "billing",
        member,
        activeRate: getActiveRate(billingRatesByMember.get(member.id) ?? []),
        currency,
      })),
    [members, billingRatesByMember, currency]
  );
  const costRows = useMemo<MemberRateRow[]>(
    () =>
      (members ?? []).map((member) => ({
        kind: "cost",
        member,
        activeRate: getActiveRate(costRatesByMember.get(member.id) ?? []),
        currency,
      })),
    [members, costRatesByMember, currency]
  );

  const { t } = createMessages("empty-states");

  if (!members || members.length === 0) {
    return (
      <EmptyState
        variant="default"
        icon={DollarSign}
        title={t("rates.settings.heading")}
        description={t("rates.settings.description")}
      />
    );
  }

  return (
    <div className="space-y-8">
      <SettingsSection
        title={
          <span className="inline-flex items-center gap-2">
            Default Currency
            <HelpTip code="rates.currency" />
          </span>
        }
        description="This currency will be pre-selected when creating new rates."
      >
        {/* Instant save: the selector saves on change, so the section has no footer. */}
        <CurrencySelector
          value={currency}
          onChange={handleCurrencyChange}
          disabled={isSavingCurrency}
        />
        {currencyMessage && (
          <p
            className={`mt-2 text-sm ${
              currencyMessage.includes("updated") ? "text-success-foreground" : "text-destructive"
            }`}
          >
            {currencyMessage}
          </p>
        )}
      </SettingsSection>

      {/* Rate snapshots info */}
      <div className="border-border bg-muted flex items-start gap-2 rounded-lg border p-4">
        <HelpTip code="rates.snapshots" />
        <p className="text-muted-foreground text-sm">
          Rate changes only affect new time entries. Existing entries and fee notes retain their
          original snapshotted rate.
        </p>
      </div>

      {/* Rates tabs */}
      <Tabs defaultValue="billing">
        <TabsList>
          <TabsTrigger value="billing">Billing Rates</TabsTrigger>
          <TabsTrigger value="cost">Cost Rates</TabsTrigger>
        </TabsList>

        <TabsContent value="billing" className="mt-4">
          <SettingsSection>
            <DataTable
              columns={billingColumns}
              data={billingRows}
              getRowId={getMemberRowId}
              rowTestId={getMemberRowTestId}
              columnVisibility={false}
              pageSize={null}
              stickyHeader={false}
              frame={false}
              empty={null}
            />
          </SettingsSection>
        </TabsContent>

        <TabsContent value="cost" className="mt-4">
          <SettingsSection
            as="h3"
            title={
              <span className="inline-flex items-center gap-2">
                Cost Rates
                <HelpTip code="rates.costVsBilling" />
              </span>
            }
          >
            <DataTable
              columns={costColumns}
              data={costRows}
              getRowId={getMemberRowId}
              rowTestId={getMemberRowTestId}
              columnVisibility={false}
              pageSize={null}
              stickyHeader={false}
              frame={false}
              empty={null}
            />
          </SettingsSection>
        </TabsContent>
      </Tabs>
    </div>
  );
}
