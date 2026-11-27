import { PageHeader } from "@b2mash/ui/page-header";
import { listAccessRequests } from "./actions";
import { AccessRequestsTable } from "@/components/access-request/access-requests-table";

export default async function AccessRequestsPage() {
  const result = await listAccessRequests();
  const requests = result.data ?? [];

  return (
    <div className="mx-auto max-w-7xl px-6 py-10">
      <PageHeader
        title="Access Requests"
        description="Review and manage organization access requests."
      />
      {!result.success && (
        <div className="mt-4 rounded-md bg-red-50 p-4 text-sm text-red-700">
          Failed to load access requests: {result.error}
        </div>
      )}
      <div className="mt-8">
        <AccessRequestsTable requests={requests} />
      </div>
    </div>
  );
}
