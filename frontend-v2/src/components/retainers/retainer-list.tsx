"use client";

import { useRouter } from "next/navigation";
import { RefreshCcw } from "lucide-react";

import type { RetainerResponse, RetainerStatus } from "@/lib/api/retainers";
import { DataTable } from "@/components/ui/data-table";
import { DataTableEmpty } from "@/components/ui/data-table-empty";
import { retainerColumns } from "./retainer-columns";

interface RetainerListProps {
  retainers: RetainerResponse[];
  orgSlug: string;
}

export function RetainerList({ retainers, orgSlug }: RetainerListProps) {
  const router = useRouter();

  return (
    <DataTable
      columns={retainerColumns}
      data={retainers}
      onRowClick={(row) =>
        router.push(`/org/${orgSlug}/retainers/${row.id}`)
      }
      emptyState={
        <DataTableEmpty
          icon={<RefreshCcw />}
          title="No retainers found"
          description="Create a retainer agreement for recurring client work."
        />
      }
    />
  );
}
