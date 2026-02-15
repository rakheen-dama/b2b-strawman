"use client";

import { useOrganization } from "@clerk/nextjs";
import { Users } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { EmptyState } from "@/components/empty-state";
import { formatDate } from "@/lib/format";

const ROLE_BADGES: Record<string, { label: string; variant: "owner" | "admin" | "member" }> = {
  "org:owner": { label: "Owner", variant: "owner" },
  "org:admin": { label: "Admin", variant: "admin" },
  "org:member": { label: "Member", variant: "member" },
};

export function MemberList() {
  const { memberships, isLoaded } = useOrganization({
    memberships: {
      infinite: true,
      keepPreviousData: true,
    },
  });

  if (!isLoaded) {
    return <div className="py-8 text-center text-sm text-slate-600 dark:text-slate-400">Loading members...</div>;
  }

  if (!memberships?.data?.length) {
    return (
      <EmptyState
        icon={Users}
        title="No members found"
        description="Organization members will appear here"
      />
    );
  }

  return (
    <div className="space-y-4">
      {/* Catalyst-style table */}
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-800">
              <th className="pb-3 pr-4 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Member
              </th>
              <th className="w-[200px] pb-3 pr-4 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Email
              </th>
              <th className="w-[100px] pb-3 pr-4 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Role
              </th>
              <th className="w-[140px] pb-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Joined
              </th>
            </tr>
          </thead>
          <tbody>
            {memberships.data.map((member) => {
              const fullName =
                `${member.publicUserData?.firstName ?? ""} ${member.publicUserData?.lastName ?? ""}`.trim() ||
                "Unknown";
              const roleInfo = ROLE_BADGES[member.role] ?? {
                label: member.role,
                variant: "member" as const,
              };
              return (
                <tr
                  key={member.id}
                  className="border-b border-slate-100 transition-colors hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/30"
                >
                  <td className="py-3 pr-4">
                    <div className="flex items-center gap-3">
                      <AvatarCircle name={fullName} size={32} />
                      <span className="font-medium text-slate-900 dark:text-slate-100">
                        {fullName}
                      </span>
                    </div>
                  </td>
                  <td className="py-3 pr-4 text-slate-600 dark:text-slate-400">
                    {member.publicUserData?.identifier ?? "—"}
                  </td>
                  <td className="py-3 pr-4">
                    <Badge variant={roleInfo.variant}>{roleInfo.label}</Badge>
                  </td>
                  <td className="py-3 text-slate-600 dark:text-slate-400">
                    {member.createdAt ? formatDate(member.createdAt) : "—"}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {memberships.hasNextPage && (
        <div className="flex justify-center">
          <button
            onClick={() => memberships.fetchNext?.()}
            disabled={memberships.isFetching}
            className="text-sm font-medium text-slate-600 hover:text-slate-900 disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-600 dark:text-slate-400 dark:hover:text-slate-200"
          >
            {memberships.isFetching ? "Loading..." : "Load more"}
          </button>
        </div>
      )}
    </div>
  );
}
