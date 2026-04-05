import { notFound } from "next/navigation";
import { getOrgSettings } from "@/lib/api/settings";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import { fetchTrustAccounts } from "@/app/(app)/org/[slug]/trust-accounting/actions";
import { TrustReportsClient } from "./TrustReportsClient";

// -- Trust report definitions (hardcoded to match 7 seeded types) ---------

const TRUST_REPORTS = [
  {
    slug: "TRUST_RECEIPTS_PAYMENTS",
    name: "Receipts & Payments Journal",
    description:
      "Journal of all trust receipts and payments for a given period.",
    parameterType: "date_range" as const,
  },
  {
    slug: "CLIENT_TRUST_BALANCES",
    name: "Client Trust Balances",
    description:
      "Summary of all client trust balances as at a specific date.",
    parameterType: "as_of_date" as const,
  },
  {
    slug: "CLIENT_LEDGER_STATEMENT",
    name: "Client Ledger Statement",
    description:
      "Detailed ledger statement for a specific client over a period.",
    parameterType: "client_date_range" as const,
  },
  {
    slug: "INVESTMENT_REGISTER",
    name: "Investment Register",
    description:
      "Register of all trust investments and their current status.",
    parameterType: "as_of_date" as const,
  },
  {
    slug: "INTEREST_ALLOCATION",
    name: "Interest Allocation",
    description:
      "Interest allocation details for a specific interest run.",
    parameterType: "interest_run" as const,
  },
  {
    slug: "TRUST_RECONCILIATION",
    name: "Trust Reconciliation",
    description:
      "Reconciliation report comparing bank statement to trust records.",
    parameterType: "reconciliation" as const,
  },
  {
    slug: "SECTION_35_DATA_PACK",
    name: "Section 35 Data Pack",
    description:
      "Compliance data pack required under Section 35 of the Attorneys Act.",
    parameterType: "financial_year" as const,
  },
] as const;

export type TrustReportDefinition = (typeof TRUST_REPORTS)[number];

// -- Page -----------------------------------------------------------------

export default async function TrustReportsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug: _slug } = await params;

  // Module gating
  let settings;
  try {
    settings = await getOrgSettings();
  } catch {
    notFound();
  }

  const enabledModules = settings.enabledModules ?? [];
  if (!enabledModules.includes("trust_accounting")) {
    notFound();
  }

  // Capability check
  const capData = await fetchMyCapabilities();
  const hasViewTrust =
    capData.isAdmin ||
    capData.isOwner ||
    capData.capabilities.includes("VIEW_TRUST");
  if (!hasViewTrust) {
    notFound();
  }

  // Fetch primary trust account for pre-filling parameters
  let accountId: string | null = null;
  try {
    const accounts = await fetchTrustAccounts();
    const primary = accounts.find((a) => a.isPrimary) ?? accounts[0];
    if (primary) {
      accountId = primary.id;
    }
  } catch {
    // Non-fatal — user can still see reports but won't have pre-filled account
  }

  return (
    <div className="space-y-8" data-testid="trust-reports-page">
      {/* Header */}
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Trust Reports
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Generate and download trust accounting reports
        </p>
      </div>

      {/* Report Cards Grid */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {TRUST_REPORTS.map((report) => (
          <Card
            key={report.slug}
            className="h-full"
            data-testid={`report-card-${report.slug}`}
          >
            <CardHeader>
              <CardTitle className="text-slate-950 dark:text-slate-50">
                {report.name}
              </CardTitle>
              <CardDescription>{report.description}</CardDescription>
            </CardHeader>
            <CardContent>
              <TrustReportsClient
                reportSlug={report.slug}
                reportName={report.name}
                parameterType={report.parameterType}
                accountId={accountId}
              />
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
