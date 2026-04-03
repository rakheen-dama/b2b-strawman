import type { InformationRequestResponse } from "@/lib/api/information-requests";
import { RequestStatusBadge } from "@/components/information-requests/request-status-badge";
import { RequestProgressBar } from "@/components/information-requests/request-progress-bar";
import { formatDate } from "@/lib/format";
import { FileText } from "lucide-react";
import { docsLink } from "@/lib/docs";
import Link from "next/link";

interface RequestListProps {
  requests: InformationRequestResponse[];
  slug: string;
  showCustomer?: boolean;
}

export function RequestList({
  requests,
  slug,
  showCustomer = true,
}: RequestListProps) {
  if (requests.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-slate-300 py-12 dark:border-slate-700">
        <FileText className="mb-3 size-8 text-slate-400 dark:text-slate-500" />
        <p className="text-sm text-slate-500 dark:text-slate-400">
          No information requests yet
        </p>
        <a
          href={docsLink("/features/information-requests")}
          target="_blank"
          rel="noopener noreferrer"
          className="mt-2 text-sm text-teal-600 hover:text-teal-700"
        >
          Read the guide
        </a>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 dark:border-slate-800">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900">
            <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
              Request
            </th>
            {showCustomer && (
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Customer
              </th>
            )}
            <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
              Contact
            </th>
            <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
              Status
            </th>
            <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
              Progress
            </th>
            <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
              Sent
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
          {requests.map((request) => (
            <tr
              key={request.id}
              className="transition-colors hover:bg-slate-50 dark:hover:bg-slate-900/50"
            >
              <td className="px-4 py-3">
                <Link
                  href={`/org/${slug}/information-requests/${request.id}`}
                  className="font-medium text-teal-600 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
                >
                  {request.requestNumber}
                </Link>
                {request.projectName && (
                  <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
                    {request.projectName}
                  </p>
                )}
              </td>
              {showCustomer && (
                <td className="px-4 py-3 text-slate-700 dark:text-slate-300">
                  {request.customerName}
                </td>
              )}
              <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                {request.portalContactName}
              </td>
              <td className="px-4 py-3">
                <RequestStatusBadge status={request.status} />
              </td>
              <td className="px-4 py-3">
                <RequestProgressBar
                  totalItems={request.totalItems}
                  acceptedItems={request.acceptedItems}
                />
              </td>
              <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                {request.sentAt ? formatDate(request.sentAt) : "—"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
