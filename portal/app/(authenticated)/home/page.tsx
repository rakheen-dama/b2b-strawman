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
import type {
  PortalInvoice,
  PortalPendingAcceptance,
} from "@/lib/types";

interface InfoRequestSummary {
  id: string;
  status: string;
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
    portalGet<InfoRequestSummary[]>("/portal/information-requests?status=PENDING")
      .then((data) => setCount(Array.isArray(data) ? data.length : 0))
      .catch(() => setCount(0));
  }, []);
  return (
    <Link href="/requests" className="block">
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
    portalGet<PortalPendingAcceptance[]>(
      "/portal/acceptance-requests/pending",
    )
      .then((data) => setCount(Array.isArray(data) ? data.length : 0))
      .catch(() => setCount(0));
  }, []);
  return (
    <Link href="/acceptance" className="block">
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
    portalGet<DeadlineSummary[]>("/portal/deadlines?within=14d")
      .then((data) => setCount(Array.isArray(data) ? data.length : 0))
      .catch(() => setCount(0));
  }, []);
  return (
    <Link href="/deadlines" className="block">
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
  const [invoices, setInvoices] = useState<PortalInvoice[] | null>(null);
  useEffect(() => {
    portalGet<PortalInvoice[]>("/portal/invoices")
      .then((data) => {
        if (!Array.isArray(data)) {
          setInvoices([]);
          return;
        }
        const sorted = [...data].sort((a, b) =>
          b.issueDate.localeCompare(a.issueDate),
        );
        setInvoices(sorted.slice(0, 3));
      })
      .catch(() => setInvoices([]));
  }, []);
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm text-slate-600">
          <Receipt className="size-4" /> Recent invoices
        </CardTitle>
      </CardHeader>
      <CardContent>
        {invoices === null ? (
          <p className="text-sm text-slate-500">Loading...</p>
        ) : invoices.length === 0 ? (
          <p className="text-sm text-slate-500">No invoices yet.</p>
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
    portalGet<TrustMovement[]>("/portal/trust/movements?limit=1")
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) {
          setMovement(data[0]);
        } else {
          setMovement(null);
        }
      })
      .catch(() => setMovement(null));
  }, []);
  return (
    <Link href="/trust" className="block">
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
