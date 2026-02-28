import { Mail, Phone, Hash, Building2, Calendar, User } from "lucide-react";

import type { Customer } from "@/lib/types";
import { formatDate } from "@/lib/format";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";

interface CustomerOverviewProps {
  customer: Customer;
}

export function CustomerOverview({ customer }: CustomerOverviewProps) {
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      {/* Contact Information */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Contact Information</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <DetailRow
            icon={<Mail className="size-4" />}
            label="Email"
            value={customer.email}
          />
          <DetailRow
            icon={<Phone className="size-4" />}
            label="Phone"
            value={customer.phone}
          />
          <DetailRow
            icon={<Hash className="size-4" />}
            label="ID / Reg Number"
            value={customer.idNumber}
          />
          <DetailRow
            icon={<Building2 className="size-4" />}
            label="Type"
            value={
              customer.customerType
                ? customer.customerType.charAt(0) +
                  customer.customerType.slice(1).toLowerCase()
                : null
            }
          />
        </CardContent>
      </Card>

      {/* Details */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Details</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <DetailRow
            icon={<Calendar className="size-4" />}
            label="Created"
            value={formatDate(customer.createdAt)}
          />
          <DetailRow
            icon={<User className="size-4" />}
            label="Created by"
            value={customer.createdByName}
          />
          <div className="flex items-start gap-3">
            <div className="mt-0.5 text-slate-400">
              <Calendar className="size-4" />
            </div>
            <div>
              <p className="text-xs font-medium text-slate-500">Status</p>
              <div className="mt-0.5">
                <StatusBadge
                  status={customer.lifecycleStatus ?? customer.status}
                />
              </div>
            </div>
          </div>
          {customer.lifecycleStatusChangedAt && (
            <DetailRow
              icon={<Calendar className="size-4" />}
              label="Status changed"
              value={formatDate(customer.lifecycleStatusChangedAt)}
            />
          )}
        </CardContent>
      </Card>

      {/* Notes */}
      {customer.notes && (
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-base">Notes</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-slate-600 whitespace-pre-wrap">
              {customer.notes}
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

function DetailRow({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string | null | undefined;
}) {
  return (
    <div className="flex items-start gap-3">
      <div className="mt-0.5 text-slate-400">{icon}</div>
      <div>
        <p className="text-xs font-medium text-slate-500">{label}</p>
        <p className="text-sm text-slate-900">
          {value || <span className="text-slate-400">--</span>}
        </p>
      </div>
    </div>
  );
}
