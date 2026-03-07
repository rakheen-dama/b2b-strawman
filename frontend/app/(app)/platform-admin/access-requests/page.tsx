import { listAccessRequests } from "./actions";
import { AccessRequestsTable } from "@/components/access-request/access-requests-table";

export default async function AccessRequestsPage() {
  const result = await listAccessRequests();
  const requests = result.data ?? [];

  return (
    <div className="mx-auto max-w-6xl px-6 py-10">
      <h1 className="text-2xl font-bold tracking-tight">Access Requests</h1>
      <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">
        Review and manage organization access requests.
      </p>
      <div className="mt-8">
        <AccessRequestsTable requests={requests} />
      </div>
    </div>
  );
}
