"use client";

import { useState, useEffect } from "react";
import { ClipboardList, Plus } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";

interface DataRequest {
  id: string;
  type: "ACCESS" | "DELETION";
  customerName: string;
  status: string;
  createdAt: string;
  deadline: string;
}

interface DataRequestsProps {
  slug: string;
}

export function DataRequests({ slug }: DataRequestsProps) {
  const [requests, setRequests] = useState<DataRequest[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const res = await fetch("/api/data-requests");
        if (res.ok) {
          const data = await res.json();
          setRequests(data.content ?? data ?? []);
        }
      } catch {
        // Non-fatal
      } finally {
        setIsLoading(false);
      }
    }
    load();
  }, []);

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-slate-900">
            Data Requests
          </h2>
          <p className="mt-1 text-sm text-slate-500">
            Track data access and deletion requests for POPIA / GDPR
            compliance.
          </p>
        </div>
        <Button size="sm" variant="outline">
          <Plus className="mr-1.5 size-4" />
          New request
        </Button>
      </div>

      {isLoading ? (
        <p className="mt-6 text-center text-sm text-slate-500">Loading...</p>
      ) : requests.length === 0 ? (
        <div className="mt-4">
          <EmptyState
            icon={ClipboardList}
            title="No data requests"
            description="Data access and deletion requests will appear here."
          />
        </div>
      ) : (
        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200">
                <th className="pb-3 pr-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                  Customer
                </th>
                <th className="pb-3 pr-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                  Type
                </th>
                <th className="pb-3 pr-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                  Status
                </th>
                <th className="pb-3 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                  Deadline
                </th>
              </tr>
            </thead>
            <tbody>
              {requests.map((req) => (
                <tr
                  key={req.id}
                  className="border-b border-slate-100 last:border-0"
                >
                  <td className="py-3 pr-4 font-medium text-slate-900">
                    {req.customerName}
                  </td>
                  <td className="py-3 pr-4">
                    <Badge
                      variant={
                        req.type === "DELETION" ? "destructive" : "neutral"
                      }
                    >
                      {req.type}
                    </Badge>
                  </td>
                  <td className="py-3 pr-4">
                    <Badge
                      variant={
                        req.status === "COMPLETED" ? "success" : "warning"
                      }
                    >
                      {req.status}
                    </Badge>
                  </td>
                  <td className="py-3 text-slate-500">{req.deadline}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
