"use client";

import { useOrganization } from "@clerk/nextjs";
import { Users } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { EmptyState } from "@/components/empty-state";
import { useOrgMembers } from "@/lib/auth/client";
import { formatDate } from "@/lib/format";
import { useState, useEffect } from "react";
import { listMembers } from "@/app/(app)/org/[slug]/team/actions";
import type { BffMember } from "@/app/(app)/org/[slug]/team/actions";
import { MemberDetailPanel } from "@/components/team/member-detail-panel";
import type { OrgRole } from "@/lib/api/org-roles";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

const ROLE_BADGES: Record<
  string,
  { label: string; variant: "owner" | "admin" | "member" }
> = {
  "org:owner": { label: "Owner", variant: "owner" },
  "org:admin": { label: "Admin", variant: "admin" },
  "org:member": { label: "Member", variant: "member" },
};

interface MemberListProps {
  isAdmin?: boolean;
  roles: OrgRole[];
  slug: string;
}

interface InnerMemberListProps {
  isAdmin?: boolean;
  onRowClick: (member: MemberRowData) => void;
}

interface MemberRowData {
  id: string;
  name: string;
  email: string;
  role: string;
  joinedAt: string;
  orgRoleName?: string;
  capabilityOverridesCount?: number;
}

function ClerkMemberList({
  isAdmin,
  onRowClick,
}: InnerMemberListProps) {
  const { memberships, isLoaded } = useOrganization({
    memberships: {
      infinite: true,
      keepPreviousData: true,
    },
  });

  if (!isLoaded) {
    return (
      <div className="py-8 text-center text-sm text-slate-600 dark:text-slate-400" aria-live="polite">
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
          const row: MemberRowData = {
            id: member.id,
            name: fullName,
            email: member.publicUserData?.identifier ?? "\u2014",
            role: member.role,
            joinedAt: member.createdAt ? formatDate(member.createdAt) : "\u2014",
          };
          return (
            <MemberRow
              key={member.id}
              member={row}
              isAdmin={isAdmin}
              onRowClick={onRowClick}
            />
          );
        })}
      </MemberTable>

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

function MockMemberList({
  isAdmin,
  onRowClick,
}: InnerMemberListProps) {
  const { members, isLoaded } = useOrgMembers();

  if (!isLoaded) {
    return (
      <div className="py-8 text-center text-sm text-slate-600 dark:text-slate-400">
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
        const row: MemberRowData = {
          id: member.id,
          name: member.name ?? "Unknown",
          email: member.email,
          role: member.role,
          joinedAt: "\u2014",
        };
        return (
          <MemberRow
            key={member.id}
            member={row}
            isAdmin={isAdmin}
            onRowClick={onRowClick}
          />
        );
      })}
    </MemberTable>
  );
}

function KeycloakBffMemberList({
  isAdmin,
  onRowClick,
}: InnerMemberListProps) {
  const [members, setMembers] = useState<BffMember[]>([]);
  const [isLoaded, setIsLoaded] = useState(false);

  useEffect(() => {
    listMembers()
      .then((data) => {
        setMembers(data);
      })
      .finally(() => setIsLoaded(true));
  }, []);

  if (!isLoaded) {
    return (
      <div className="py-8 text-center text-sm text-slate-600 dark:text-slate-400">
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
        const row: MemberRowData = {
          id: member.id,
          name: member.name ?? "Unknown",
          email: member.email,
          role: member.role,
          joinedAt: "\u2014",
          orgRoleName: member.orgRoleName,
          capabilityOverridesCount: member.capabilityOverridesCount,
        };
        return (
          <MemberRow
            key={member.id}
            member={row}
            isAdmin={isAdmin}
            onRowClick={onRowClick}
          />
        );
      })}
    </MemberTable>
  );
}

// --- Shared UI components ---

function MemberTable({ children }: { children: React.ReactNode }) {
  return (
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
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

function MemberRow({
  member,
  isAdmin,
  onRowClick,
}: {
  member: MemberRowData;
  isAdmin?: boolean;
  onRowClick?: (member: MemberRowData) => void;
}) {
  const roleInfo = ROLE_BADGES[member.role];
  const isSystemRole = !!roleInfo;

  return (
    <tr
      className={`border-b border-slate-100 transition-colors hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/30${isAdmin ? " cursor-pointer" : ""}`}
      onClick={() => isAdmin && onRowClick?.(member)}
      role={isAdmin ? "button" : undefined}
      tabIndex={isAdmin ? 0 : undefined}
      onKeyDown={(e) => {
        if (isAdmin && (e.key === "Enter" || e.key === " ")) {
          e.preventDefault();
          onRowClick?.(member);
        }
      }}
    >
      <td className="py-3 pr-4">
        <div className="flex items-center gap-3">
          <AvatarCircle name={member.name} size={32} />
          <span className="font-medium text-slate-900 dark:text-slate-100">
            {member.name}
          </span>
        </div>
      </td>
      <td className="py-3 pr-4 text-slate-600 dark:text-slate-400">
        {member.email}
      </td>
      <td className="py-3 pr-4">
        <div className="flex items-center gap-1.5">
          {isSystemRole ? (
            <Badge variant={roleInfo.variant}>{roleInfo.label}</Badge>
          ) : (
            <Badge variant="neutral">
              {member.orgRoleName ?? member.role}
            </Badge>
          )}
          {(member.capabilityOverridesCount ?? 0) > 0 && (
            <span className="text-xs font-medium text-teal-600" data-testid="override-count">
              +{member.capabilityOverridesCount}
            </span>
          )}
        </div>
      </td>
      <td className="py-3 text-slate-600 dark:text-slate-400">
        {member.joinedAt}
      </td>
    </tr>
  );
}

export function MemberList({ isAdmin, roles, slug }: MemberListProps) {
  const [selectedMember, setSelectedMember] = useState<MemberRowData | null>(
    null,
  );
  const [panelOpen, setPanelOpen] = useState(false);

  function handleRowClick(member: MemberRowData) {
    if (!isAdmin) return;
    setSelectedMember(member);
    setPanelOpen(true);
  }

  const innerProps: InnerMemberListProps = { isAdmin, onRowClick: handleRowClick };

  return (
    <>
      {AUTH_MODE === "mock" ? (
        <MockMemberList {...innerProps} />
      ) : AUTH_MODE === "keycloak" ? (
        <KeycloakBffMemberList {...innerProps} />
      ) : (
        <ClerkMemberList {...innerProps} />
      )}

      {isAdmin && (
        <MemberDetailPanel
          open={panelOpen}
          onOpenChange={setPanelOpen}
          member={selectedMember}
          roles={roles}
          slug={slug}
        />
      )}
    </>
  );
}
