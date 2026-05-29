"use client";

import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Briefcase } from "lucide-react";
import { CustomerAddressBlock } from "@/components/customers/customer-address-block";
import { CustomerContactCard } from "@/components/customers/customer-contact-card";
import { ENTITY_TYPES } from "@/lib/constants/entity-types";
import { formatDate } from "@/lib/format";
import type { Customer } from "@/lib/types";

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface ClientDetailsTabProps {
  customer: Customer;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function ClientDetailsTab({ customer }: ClientDetailsTabProps) {
  const hasBusinessDetails = !!(
    customer.registrationNumber ||
    customer.taxNumber ||
    customer.entityType ||
    customer.financialYearEnd
  );

  return (
    <div data-testid="client-details-tab" className="space-y-6">
      {/* Address + Contact: 2-column on desktop, single on mobile */}
      <div className="grid gap-6 md:grid-cols-2">
        <CustomerAddressBlock customer={customer} />
        <CustomerContactCard customer={customer} />
      </div>

      {/* Business Details — only when at least one field present */}
      {hasBusinessDetails && (
        <Card data-testid="customer-business-details">
          <CardHeader>
            <div className="flex items-center gap-3">
              <Briefcase className="size-5 text-slate-400" />
              <CardTitle>Business Details</CardTitle>
            </div>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              {customer.registrationNumber && (
                <div>
                  <dt className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                    Registration Number
                  </dt>
                  <dd className="mt-1 text-slate-700 dark:text-slate-300">
                    {customer.registrationNumber}
                  </dd>
                </div>
              )}
              {customer.taxNumber && (
                <div>
                  <dt className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                    Tax Number
                  </dt>
                  <dd className="mt-1 text-slate-700 dark:text-slate-300">{customer.taxNumber}</dd>
                </div>
              )}
              {customer.entityType && (
                <div>
                  <dt className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                    Entity Type
                  </dt>
                  <dd className="mt-1 text-slate-700 dark:text-slate-300">
                    {ENTITY_TYPES.find((e) => e.value === customer.entityType)?.label ??
                      customer.entityType}
                  </dd>
                </div>
              )}
              {customer.financialYearEnd && (
                <div>
                  <dt className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                    Financial Year End
                  </dt>
                  <dd className="mt-1 text-slate-700 dark:text-slate-300">
                    {formatDate(new Date(customer.financialYearEnd + "T00:00:00"))}
                  </dd>
                </div>
              )}
            </dl>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
