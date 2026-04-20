"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Download, FileText } from "lucide-react";
import { usePortalContext } from "@/hooks/use-portal-context";
import { BalanceCard } from "@/components/trust/balance-card";
import { TransactionList } from "@/components/trust/transaction-list";
import { Skeleton } from "@/components/ui/skeleton";
import { formatDate } from "@/lib/format";
import {
  getMatterStatementDocuments,
  getTrustSummary,
  type PortalTrustMatterSummary,
  type PortalTrustStatementDocumentResponse,
} from "@/lib/api/trust";

function DetailSkeleton() {
  return (
    <div className="space-y-8">
      <Skeleton className="h-4 w-32" />
      <Skeleton className="h-40 w-full md:w-96" />
      <Skeleton className="h-48 w-full" />
    </div>
  );
}

export default function TrustMatterDetailPage() {
  const params = useParams();
  const matterId = Array.isArray(params.matterId)
    ? params.matterId[0]
    : (params.matterId ?? "");
  const ctx = usePortalContext();
  const router = useRouter();

  const [summary, setSummary] = useState<PortalTrustMatterSummary | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  const [statements, setStatements] = useState<
    PortalTrustStatementDocumentResponse[]
  >([]);
  const [statementsLoading, setStatementsLoading] = useState(true);

  // Module gate: redirect once context has loaded if the module is disabled.
  useEffect(() => {
    if (ctx && !ctx.enabledModules.includes("trust_accounting")) {
      router.replace("/home");
    }
  }, [ctx, router]);

  // Fetch per-matter balance snapshot from the summary endpoint.
  useEffect(() => {
    if (!matterId) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await getTrustSummary();
        if (cancelled) return;
        const match = res.matters.find((m) => m.matterId === matterId) ?? null;
        setSummary(match);
      } catch (err) {
        if (!cancelled) {
          setSummaryError(
            err instanceof Error
              ? err.message
              : "Failed to load trust balance",
          );
        }
      } finally {
        if (!cancelled) setSummaryLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [matterId]);

  // Fetch statement documents (endpoint currently returns [] until the
  // STATEMENT category lands — empty state is expected).
  useEffect(() => {
    if (!matterId) return;
    let cancelled = false;
    (async () => {
      try {
        const docs = await getMatterStatementDocuments(matterId);
        if (!cancelled) setStatements(docs);
      } catch {
        if (!cancelled) setStatements([]);
      } finally {
        if (!cancelled) setStatementsLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [matterId]);

  if (summaryLoading) {
    return <DetailSkeleton />;
  }

  if (summaryError) {
    return (
      <div className="space-y-4">
        <Link
          href="/trust"
          className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
        >
          <ArrowLeft className="size-4" />
          Back to trust
        </Link>
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {summaryError}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <Link
        href="/trust"
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="size-4" />
        Back to trust
      </Link>

      {summary ? (
        <BalanceCard
          matterId={summary.matterId}
          currentBalance={summary.currentBalance}
          lastTransactionAt={summary.lastTransactionAt}
        />
      ) : (
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
          No trust balance is recorded for this matter.
        </div>
      )}

      <section>
        <h2 className="font-display mb-4 text-lg font-semibold text-slate-900">
          Transactions
        </h2>
        <TransactionList matterId={matterId} />
      </section>

      <section>
        <h2 className="font-display mb-4 text-lg font-semibold text-slate-900">
          Statements
        </h2>
        {statementsLoading ? (
          <Skeleton className="h-16 w-full" />
        ) : statements.length === 0 ? (
          <div className="flex flex-col items-center justify-center rounded-lg border border-slate-200 bg-white py-10 text-center">
            <FileText
              className="mb-3 size-8 text-slate-300"
              aria-hidden="true"
            />
            <p className="text-sm text-slate-600">
              No statement documents yet
            </p>
          </div>
        ) : (
          <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
            {statements.map((doc) => (
              <li
                key={doc.id}
                className="flex items-center justify-between gap-4 px-4 py-3"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-slate-900">
                    {doc.fileName}
                  </p>
                  <p className="text-xs text-slate-500">
                    {formatDate(doc.generatedAt)}
                  </p>
                </div>
                <a
                  href={doc.downloadUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex min-h-[40px] items-center gap-1.5 rounded-md text-sm font-medium text-teal-600 hover:text-teal-700"
                  aria-label={`Download ${doc.fileName}`}
                >
                  <Download className="size-4" />
                  Download
                </a>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
