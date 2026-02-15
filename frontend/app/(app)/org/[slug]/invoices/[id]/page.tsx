import { auth } from "@clerk/nextjs/server";
import { api, handleApiError } from "@/lib/api";
import type { InvoiceResponse } from "@/lib/types";
import { InvoiceDetailClient } from "@/components/invoices/invoice-detail-client";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";

export default async function InvoiceDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Invoice
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to view invoices. Only admins and owners can
          access this page.
        </p>
      </div>
    );
  }

  let invoice: InvoiceResponse;
  try {
    invoice = await api.get<InvoiceResponse>(`/api/invoices/${id}`);
  } catch (error) {
    handleApiError(error);
  }

  return (
    <div className="space-y-8">
      {/* Back link */}
      <div>
        <Link
          href={`/org/${slug}/invoices`}
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Invoices
        </Link>
      </div>

      <InvoiceDetailClient
        invoice={invoice!}
        slug={slug}
        isAdmin={isAdmin}
      />
    </div>
  );
}
