"use client";

import { useState, useTransition } from "react";
import { Plus, Users, X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/empty-state";
import { Button } from "@/components/ui/button";
import { LinkCustomerDialog } from "@/components/projects/link-customer-dialog";
import { unlinkCustomerFromProject } from "@/app/(app)/org/[slug]/projects/[id]/actions";
import { formatDate } from "@/lib/format";
import type { Customer } from "@/lib/types";
import Link from "next/link";

interface ProjectCustomersPanelProps {
  customers: Customer[];
  slug: string;
  projectId: string;
  canManage: boolean;
}

export function ProjectCustomersPanel({
  customers,
  slug,
  projectId,
  canManage,
}: ProjectCustomersPanelProps) {
  const [isPending, startTransition] = useTransition();
  const [unlinkingCustomerId, setUnlinkingCustomerId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function handleUnlink(customerId: string) {
    setError(null);
    setUnlinkingCustomerId(customerId);

    startTransition(async () => {
      try {
        const result = await unlinkCustomerFromProject(slug, projectId, customerId);
        if (!result.success) {
          setError(result.error ?? "Failed to unlink customer.");
        }
      } catch {
        setError("An unexpected error occurred.");
      } finally {
        setUnlinkingCustomerId(null);
      }
    });
  }

  const header = (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <h2 className="font-semibold text-olive-900 dark:text-olive-100">Customers</h2>
        {customers.length > 0 && <Badge variant="neutral">{customers.length}</Badge>}
      </div>
      {canManage && (
        <LinkCustomerDialog
          slug={slug}
          projectId={projectId}
          existingCustomers={customers}
        >
          <Button size="sm" variant="outline">
            <Plus className="mr-1.5 size-4" />
            Link Customer
          </Button>
        </LinkCustomerDialog>
      )}
    </div>
  );

  if (customers.length === 0) {
    return (
      <div className="space-y-4">
        {header}
        <EmptyState
          icon={Users}
          title="No linked customers"
          description="Link customers to this project to track client work"
        />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {header}
      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-olive-200 dark:border-olive-800">
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
                Name
              </th>
              <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                Email
              </th>
              <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-olive-600 lg:table-cell dark:text-olive-400">
                Status
              </th>
              {canManage && (
                <th className="w-[60px] px-4 py-3" />
              )}
            </tr>
          </thead>
          <tbody>
            {customers.map((customer) => {
              const isUnlinking = unlinkingCustomerId === customer.id;

              return (
                <tr
                  key={customer.id}
                  className="group border-b border-olive-100 transition-colors last:border-0 hover:bg-olive-50 dark:border-olive-800/50 dark:hover:bg-olive-900/50"
                >
                  <td className="px-4 py-3">
                    <Link
                      href={`/org/${slug}/customers/${customer.id}`}
                      className="font-medium text-olive-950 hover:underline dark:text-olive-50"
                    >
                      {customer.name}
                    </Link>
                  </td>
                  <td className="hidden px-4 py-3 text-sm text-olive-600 sm:table-cell dark:text-olive-400">
                    {customer.email || "\u2014"}
                  </td>
                  <td className="hidden px-4 py-3 text-sm lg:table-cell">
                    <Badge variant={customer.status === "ACTIVE" ? "success" : "neutral"}>
                      {customer.status}
                    </Badge>
                  </td>
                  {canManage && (
                    <td className="px-4 py-3">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="size-8 p-0 text-olive-400 hover:text-red-600 dark:text-olive-600 dark:hover:text-red-400"
                        onClick={() => handleUnlink(customer.id)}
                        disabled={isUnlinking || isPending}
                        title="Unlink customer"
                      >
                        <X className="size-4" />
                        <span className="sr-only">Unlink</span>
                      </Button>
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
