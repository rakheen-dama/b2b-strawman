"use client";

import useSWR from "swr";
import { ExternalLink } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { getInvoiceSyncStatusAction } from "@/app/(app)/org/[slug]/settings/integrations/xero/sync-log/actions";
import { defaultSWROptions } from "@/lib/swr/fetcher";

interface XeroContactBadgeProps {
  customerId: string;
  slug: string;
}

export function XeroContactBadge({ customerId, slug }: XeroContactBadgeProps) {
  const { data: externalId } = useSWR<string | null>(
    `xero-contact-${slug}-${customerId}`,
    async () => {
      const result = await getInvoiceSyncStatusAction(slug, customerId, "CUSTOMER");
      return result.success && result.data?.externalId ? result.data.externalId : null;
    },
    defaultSWROptions
  );

  if (!externalId) return null;

  return (
    <a
      href={`https://go.xero.com/Contacts/View/${externalId}`}
      target="_blank"
      rel="noopener noreferrer"
    >
      <Badge variant="neutral" className="bg-[#13B5EA]/10 text-[#13B5EA]">
        <ExternalLink className="mr-1 size-3" />
        Xero Contact
      </Badge>
    </a>
  );
}
