import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { MapPin } from "lucide-react";
import type { Customer } from "@/lib/types";

interface CustomerAddressBlockProps {
  customer: Customer;
}

/**
 * Display-only card that renders a formatted address for a customer.
 * Shows a "No address on file" placeholder when all address fields are empty.
 *
 * Safe to render from a Server Component — no hooks or client state.
 */
export function CustomerAddressBlock({ customer }: CustomerAddressBlockProps) {
  const cityLine = [customer.city, customer.stateProvince, customer.postalCode]
    .map((v) => (v ?? "").trim())
    .filter((v) => v !== "")
    .join(", ");

  const lines = [
    customer.addressLine1,
    customer.addressLine2,
    cityLine || null,
    customer.country,
  ].filter((l): l is string => !!l && l.trim() !== "");

  return (
    <Card data-testid="customer-address-block">
      <CardHeader>
        <div className="flex items-center gap-3">
          <MapPin className="size-5 text-slate-400" />
          <CardTitle>Address</CardTitle>
        </div>
      </CardHeader>
      <CardContent>
        {lines.length === 0 ? (
          <p className="text-sm text-muted-foreground">No address on file.</p>
        ) : (
          <address className="not-italic text-sm text-slate-700 dark:text-slate-300">
            {lines.map((line, i) => (
              <div key={i}>{line}</div>
            ))}
          </address>
        )}
      </CardContent>
    </Card>
  );
}
