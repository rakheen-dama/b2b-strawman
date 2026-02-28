"use client";

import { useOrganization } from "@clerk/nextjs";
import { Users } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { EmptyState } from "@/components/empty-state";
import { useOrgMembers } from "@/lib/auth/client";
import { formatDate } from "@/lib/format";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

const ROLE_BADGES: Record<
  string,
  { label: string; variant: "owner" | "admin" | "member" }
> = {
  "org:owner": { label: "Owner", variant: "owner" },
  "org:admin": { label: "Admin", variant: "admin" },
  "org:member": { label: "Member", variant: "member" },
};

function ClerkMemberList() {
  const { memberships, isLoaded } = useOrganization({
    memberships: { infinite: true, keepPreviousData: true },
  });

  if (!isLoaded) {
    return (
      <div className="py-8 text-center text-sm text-slate-500">
        Loading members...
      </div>
    );
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
      <MemberTable>
        {memberships.data.map((member) => {
          const fullName =
            `${member.publicUserData?.firstName ?? ""} ${member.publicUserData?.lastName ?? ""}`.trim() ||
            "Unknown";
          const roleInfo = ROLE_BADGES[member.role] ?? {
            label: member.role,
            variant: "member" as const,
          };
          return (
            <MemberRow
              key={member.id}
              name={fullName}
              email={member.publicUserData?.identifier ?? "--"}
              role={roleInfo}
              joinedAt={
                member.createdAt ? formatDate(member.createdAt) : "--"
              }
            />
          );
        })}
      </MemberTable>

      {memberships.hasNextPage && (
        <div className="flex justify-center">
          <button
            onClick={() => memberships.fetchNext?.()}
            disabled={memberships.isFetching}
            className="text-sm font-medium text-slate-600 hover:text-slate-900 disabled:opacity-50"
          >
            {memberships.isFetching ? "Loading..." : "Load more"}
          </button>
        </div>
      )}
    </div>
  );
}

function MockMemberList() {
  const { members, isLoaded } = useOrgMembers();

  if (!isLoaded) {
    return (
      <div className="py-8 text-center text-sm text-slate-500">
        Loading members...
      </div>
    );
  }

  if (!members.length) {
    return (
      <EmptyState
        icon={Users}
        title="No members found"
        description="Organization members will appear here"
      />
    );
  }

  return (
    <MemberTable>
      {members.map((member) => {
        const roleInfo = ROLE_BADGES[member.role] ?? {
          label: member.role,
          variant: "member" as const,
        };
        return (
          <MemberRow
            key={member.id}
            name={member.name ?? "Unknown"}
            email={member.email}
            role={roleInfo}
            joinedAt="--"
          />
        );
      })}
    </MemberTable>
  );
}

function MemberTable({ children }: { children: React.ReactNode }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200">
            <th className="px-4 pb-3 pt-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
              Member
            </th>
            <th className="w-[200px] px-4 pb-3 pt-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
              Email
            </th>
            <th className="w-[100px] px-4 pb-3 pt-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
              Role
            </th>
            <th className="w-[140px] px-4 pb-3 pt-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
              Joined
            </th>
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

function MemberRow({
  name,
  email,
  role,
  joinedAt,
}: {
  name: string;
  email: string;
  role: { label: string; variant: "owner" | "admin" | "member" };
  joinedAt: string;
}) {
  return (
    <tr className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50">
      <td className="px-4 py-3">
        <div className="flex items-center gap-3">
          <AvatarCircle name={name} size={32} />
          <span className="font-medium text-slate-900">{name}</span>
        </div>
      </td>
      <td className="px-4 py-3 text-slate-500">{email}</td>
      <td className="px-4 py-3">
        <Badge variant={role.variant}>{role.label}</Badge>
      </td>
      <td className="px-4 py-3 text-slate-500">{joinedAt}</td>
    </tr>
  );
}

export function MemberList() {
  if (AUTH_MODE === "mock") return <MockMemberList />;
  return <ClerkMemberList />;
}
