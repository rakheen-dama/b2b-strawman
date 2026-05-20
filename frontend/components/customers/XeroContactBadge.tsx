"use client";

import { useEffect, useState } from "react";
import { ExternalLink } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { getInvoiceSyncStatusAction } from "@/app/(app)/org/[slug]/settings/integrations/xero/sync-log/actions";

interface XeroContactBadgeProps {
  customerId: string;
  slug: string;
}

export function XeroContactBadge({ customerId, slug }: XeroContactBadgeProps) {
  const [externalId, setExternalId] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const result = await getInvoiceSyncStatusAction(slug, customerId, "CUSTOMER");
        if (!cancelled && result.success && result.data?.externalId) {
          setExternalId(result.data.externalId);
        }
      } catch {
        // Not synced or error — show nothing
      } finally {
        if (!cancelled) setLoaded(true);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [customerId, slug]);

  if (!loaded || !externalId) return null;

  return (
    <Badge variant="neutral" className="bg-[#13B5EA]/10 text-[#13B5EA]">
      <ExternalLink className="mr-1 size-3" />
      Xero Contact
    </Badge>
  );
}
