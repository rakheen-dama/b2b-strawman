import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { User, Mail, Phone } from "lucide-react";
import type { Customer } from "@/lib/types";

interface CustomerContactCardProps {
  customer: Customer;
}

/**
 * Display-only card showing the primary contact for a customer. Renders
 * name, email (as mailto link), and phone (as tel link). Shows a "No contact
 * on file" placeholder when all three fields are empty.
 *
 * Safe to render from a Server Component — no hooks or client state.
 */
export function CustomerContactCard({ customer }: CustomerContactCardProps) {
  const name = customer.contactName?.trim() || null;
  const email = customer.contactEmail?.trim() || null;
  const phone = customer.contactPhone?.trim() || null;
  const hasAny = !!(name || email || phone);

  return (
    <Card data-testid="customer-contact-card">
      <CardHeader>
        <div className="flex items-center gap-3">
          <User className="size-5 text-slate-400" />
          <CardTitle>Primary Contact</CardTitle>
        </div>
      </CardHeader>
      <CardContent>
        {!hasAny ? (
          <p className="text-sm text-muted-foreground">No contact on file.</p>
        ) : (
          <div className="space-y-2 text-sm">
            {name && (
              <div className="flex items-center gap-2 text-slate-700 dark:text-slate-300">
                <User className="size-4 text-slate-400" />
                <span>{name}</span>
              </div>
            )}
            {email && (
              <div className="flex items-center gap-2">
                <Mail className="size-4 text-slate-400" />
                <a
                  href={`mailto:${email}`}
                  className="text-teal-600 hover:underline dark:text-teal-400"
                >
                  {email}
                </a>
              </div>
            )}
            {phone && (
              <div className="flex items-center gap-2">
                <Phone className="size-4 text-slate-400" />
                <a
                  href={`tel:${phone}`}
                  className="text-teal-600 hover:underline dark:text-teal-400"
                >
                  {phone}
                </a>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
