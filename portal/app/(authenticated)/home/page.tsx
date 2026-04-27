"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  FileCheck,
  MessageSquare,
  CalendarClock,
  Receipt,
  Scale,
} from "lucide-react";
import { portalGet } from "@/lib/api-client";
import { useModules } from "@/hooks/use-portal-context";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatCurrency, formatDate } from "@/lib/format";
import { useTerminology } from "@/lib/terminology";
import type {
  PortalInvoice,
  PortalPendingAcceptance,
} from "@/lib/types";

interface InfoRequestSummary {
  id: string;
  status: string;
  totalItems: number;
  submittedItems: number;
}

interface DeadlineSummary {
  id: string;
  dueAt: string;
}

interface TrustMovement {
  id: string;
  amount: number;
  currency: string;
  occurredAt: string;
  description: string | null;
}

export default function HomePage() {
  const modules = useModules();

  return (
    <div>
      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        Home
      </h1>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {modules.includes("information_requests") && <InfoRequestsCard />}
        {modules.includes("document_acceptance") && <AcceptancesCard />}
        {modules.includes("deadlines") && <DeadlinesCard />}
        <RecentInvoicesCard />
        {modules.includes("trust_accounting") && <TrustCard />}
      </div>
    </div>
  );
}

function InfoRequestsCard() {
  const [count, setCount] = useState<number | null>(null);
  useEffect(() => {
    let cancelled = false;
    portalGet<InfoRequestSummary[]>("/portal/requests")
      .then((data) => {
        if (cancelled) return;
        const pending = Array.isArray(data)
          ? data.filter(
              (r) =>
                r.status !== "COMPLETED" && r.submittedItems < r.totalItems,
            ).length
          : 0;
        setCount(pending);
      })
      .catch(() => {
        if (!cancelled) setCount(0);
      });
    return () => {
      cancelled = true;
    };
  }, []);
  return (
    <Link
      href="/requests"
      className="block rounded-lg focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500"
    >
      <Card className="transition hover:shadow-md">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm text-slate-600">
            <MessageSquare className="size-4" /> Pending info requests
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="font-mono text-2xl font-semibold text-slate-900 tabular-nums">
            {count ?? "--"}
          </p>
        </CardContent>
      </Card>
    </Link>
  );
}

function AcceptancesCard() {
  const [count, setCount] = useState<number | null>(null);
  useEffect(() => {
    let cancelled = false;
    portalGet<PortalPendingAcceptance[]>(
      "/portal/acceptance-requests/pending",
    )
      .then((data) => {
        if (!cancelled) setCount(Array.isArray(data) ? data.length : 0);
      })
      .catch(() => {
        if (!cancelled) setCount(0);
      });
    return () => {
      cancelled = true;
    };
  }, []);
  return (
    <Link
      href="/acceptance"
      className="block rounded-lg focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500"
    >
      <Card className="transition hover:shadow-md">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm text-slate-600">
            <FileCheck className="size-4" /> Pending acceptances
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="font-mono text-2xl font-semibold text-slate-900 tabular-nums">
            {count ?? "--"}
          </p>
        </CardContent>
      </Card>
    </Link>
  );
}

function DeadlinesCard() {
  const [count, setCount] = useState<number | null>(null);
  useEffect(() => {
    let cancelled = false;
    portalGet<DeadlineSummary[]>("/portal/deadlines?within=14d")
      .then((data) => {
        if (!cancelled) setCount(Array.isArray(data) ? data.length : 0);
      })
      .catch(() => {
        if (!cancelled) setCount(0);
      });
    return () => {
      cancelled = true;
    };
  }, []);
  return (
    <Link
      href="/deadlines"
      className="block rounded-lg focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500"
    >
      <Card className="transition hover:shadow-md">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm text-slate-600">
            <CalendarClock className="size-4" /> Upcoming deadlines
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="font-mono text-2xl font-semibold text-slate-900 tabular-nums">
            {count ?? "--"}
          </p>
          <p className="mt-1 text-xs text-slate-500">Next 14 days</p>
        </CardContent>
      </Card>
    </Link>
  );
}

function RecentInvoicesCard() {
  const { t } = useTerminology();
  const [invoices, setInvoices] = useState<PortalInvoice[] | null>(null);
  useEffect(() => {
    let cancelled = false;
    portalGet<PortalInvoice[]>("/portal/invoices")
      .then((data) => {
        if (cancelled) return;
        if (!Array.isArray(data)) {
          setInvoices([]);
          return;
        }
        const sorted = [...data].sort((a, b) =>
          b.issueDate.localeCompare(a.issueDate),
        );
        setInvoices(sorted.slice(0, 3));
      })
      .catch(() => {
        if (!cancelled) setInvoices([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm text-slate-600">
          <Receipt className="size-4" /> Recent {t("invoices")}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {invoices === null ? (
          <p className="text-sm text-slate-500">Loading...</p>
        ) : invoices.length === 0 ? (
          <p className="text-sm text-slate-500">No {t("invoices")} yet.</p>
        ) : (
          <ul className="space-y-2">
            {invoices.map((inv) => (
              <li
                key={inv.id}
                className="flex justify-between gap-4 text-sm"
              >
                <Link
                  href={`/invoices/${inv.id}`}
                  className="text-teal-600 hover:underline"
                >
                  {inv.invoiceNumber}
                </Link>
                <span className="font-mono text-slate-700 tabular-nums">
                  {formatCurrency(inv.total, inv.currency)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function TrustCard() {
  const [movement, setMovement] = useState<TrustMovement | null | undefined>(
    undefined,
  );
  useEffect(() => {
    let cancelled = false;
    portalGet<TrustMovement[]>("/portal/trust/movements?limit=1")
      .then((data) => {
        if (cancelled) return;
        if (Array.isArray(data) && data.length > 0) {
          setMovement(data[0]);
        } else {
          setMovement(null);
        }
      })
      .catch(() => {
        if (!cancelled) setMovement(null);
      });
    return () => {
      cancelled = true;
    };
  }, []);
  return (
    <Link
      href="/trust"
      className="block rounded-lg focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500"
    >
      <Card className="transition hover:shadow-md">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm text-slate-600">
            <Scale className="size-4" /> Last trust movement
          </CardTitle>
        </CardHeader>
        <CardContent>
          {movement === undefined ? (
            <p className="text-sm text-slate-500">Loading...</p>
          ) : movement === null ? (
            <p className="text-sm text-slate-500">No recent activity</p>
          ) : (
            <div>
              <p className="font-mono text-lg font-semibold text-slate-900 tabular-nums">
                {formatCurrency(movement.amount, movement.currency)}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {formatDate(movement.occurredAt)}
              </p>
            </div>
          )}
        </CardContent>
      </Card>
    </Link>
  );
}
