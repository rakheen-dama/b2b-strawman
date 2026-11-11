"use client";

import { Users } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { EmptyState } from "@/components/empty-state";
import { useOrgMembers } from "@/lib/auth/client";
import { useState, useEffect } from "react";
import { listMembers } from "@/app/(app)/org/[slug]/team/member-actions";
import type { BffMember } from "@/app/(app)/org/[slug]/team/member-actions";
import { MemberDetailPanel } from "@/components/team/member-detail-panel";
import type { OrgRole } from "@/lib/api/org-roles";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";

const ROLE_BADGES: Record<string, { label: string; variant: "owner" | "admin" | "member" }> = {
  "org:owner": { label: "Owner", variant: "owner" },
  "org:admin": { label: "Admin", variant: "admin" },
  "org:member": { label: "Member", variant: "member" },
};

/** A role's slug and display name only: safe to hand to every member (no capabilities). */
export type RoleName = Pick<OrgRole, "slug" | "name">;

interface MemberListProps {
  isAdmin?: boolean;
  /** Full roles for the admin detail panel; empty for non-admins. */
  roles: OrgRole[];
  /** Custom-role display names for the Role badge, for every viewer (PR #1752 review). */
  roleNames?: RoleName[];
  slug: string;
}

interface InnerMemberListProps {
  isAdmin?: boolean;
  onRowClick: (member: MemberRowData) => void;
  roleNames: RoleName[];
  /** Bumped after a role change is saved, so the list reloads (G89-F65). */
  refreshKey: number;
}

interface MemberRowData {
  id: string;
  name: string;
  email: string;
  role: string;
  orgRoleName?: string;
  capabilityOverridesCount?: number;
}

function MockMemberList({ isAdmin, onRowClick, roleNames }: InnerMemberListProps) {
  const { members, isLoaded } = useOrgMembers();

  if (!isLoaded) {
    return (
      <div
        className="py-8 text-center text-sm text-slate-600 dark:text-slate-400"
        aria-live="polite"
      >
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
        };
        return (
          <MemberRow
            key={member.id}
            member={row}
            roleNames={roleNames}
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
  roleNames,
  refreshKey,
}: InnerMemberListProps) {
  const [members, setMembers] = useState<BffMember[]>([]);
  const [isLoaded, setIsLoaded] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    // A newer reload supersedes this one: its late response must not overwrite newer rows
    let superseded = false;
    listMembers()
      .then((data) => {
        if (superseded) return;
        if (data === null) {
          // Keep the rows already shown; a failed request is not an empty org
          setLoadFailed(true);
          return;
        }
        setLoadFailed(false);
        setMembers(data);
      })
      .finally(() => {
        if (!superseded) setIsLoaded(true);
      });
    return () => {
      superseded = true;
    };
  }, [refreshKey]);

  const loadError = loadFailed ? (
    <p className="pb-3 text-sm text-red-600 dark:text-red-400" role="alert">
      Could not refresh the member list.
    </p>
  ) : null;

  if (!isLoaded) {
    return (
      <div
        className="py-8 text-center text-sm text-slate-600 dark:text-slate-400"
        aria-live="polite"
      >
        Loading members...
      </div>
    );
  }

  if (!members.length) {
    return (
      loadError ?? (
        <EmptyState
          icon={Users}
          title="No members found"
          description="Organization members will appear here"
        />
      )
    );
  }

  return (
    <>
      {loadError}
      <MemberTable>
        {members.map((member) => {
          const row: MemberRowData = {
            id: member.id,
            name: member.name ?? "Unknown",
            email: member.email,
            role: member.role,
            orgRoleName: member.orgRoleName,
            capabilityOverridesCount: member.capabilityOverridesCount,
          };
          return (
            <MemberRow
              key={member.id}
              member={row}
              roleNames={roleNames}
              isAdmin={isAdmin}
              onRowClick={onRowClick}
            />
          );
        })}
      </MemberTable>
    </>
  );
}

// --- Shared UI components ---

function MemberTable({ children }: { children: React.ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 dark:border-slate-800">
            <th className="pr-4 pb-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Member
            </th>
            <th className="w-[200px] pr-4 pb-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Email
            </th>
            <th className="w-[100px] pr-4 pb-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Role
            </th>
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

/**
 * A custom role's display name: the gateway's orgRoleName, else the org role whose slug matches the
 * member's "org:<slug>" role, else the bare slug — never the "org:" wire form (G89-F65).
 */
function customRoleLabel(member: MemberRowData, roleNames: RoleName[]): string {
  if (member.orgRoleName) return member.orgRoleName;
  const slug = member.role.startsWith("org:") ? member.role.slice(4) : member.role;
  return roleNames.find((r) => r.slug === slug)?.name ?? slug;
}

function MemberRow({
  member,
  roleNames,
  isAdmin,
  onRowClick,
}: {
  member: MemberRowData;
  roleNames: RoleName[];
  isAdmin?: boolean;
  onRowClick?: (member: MemberRowData) => void;
}) {
  const roleInfo = ROLE_BADGES[member.role];
  const isSystemRole = !!roleInfo;

  return (
    <tr
      data-testid={`member-row-${member.email}`}
      className={`border-b border-slate-100 transition-colors hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/30${isAdmin ? "cursor-pointer" : ""}`}
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
          <span className="font-medium text-slate-900 dark:text-slate-100">{member.name}</span>
        </div>
      </td>
      <td className="py-3 pr-4 text-slate-600 dark:text-slate-400">{member.email}</td>
      <td className="py-3 pr-4">
        <div className="flex items-center gap-1.5">
          {isSystemRole ? (
            <Badge variant={roleInfo.variant} data-testid="member-role-badge">
              {roleInfo.label}
            </Badge>
          ) : (
            <Badge variant="neutral" data-testid="member-role-badge">
              {customRoleLabel(member, roleNames)}
            </Badge>
          )}
          {(member.capabilityOverridesCount ?? 0) > 0 && (
            <span className="text-xs font-medium text-teal-600" data-testid="override-count">
              +{member.capabilityOverridesCount}
            </span>
          )}
        </div>
      </td>
    </tr>
  );
}

export function MemberList({ isAdmin, roles, roleNames, slug }: MemberListProps) {
  const [selectedMember, setSelectedMember] = useState<MemberRowData | null>(null);
  const [panelOpen, setPanelOpen] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  function handleRowClick(member: MemberRowData) {
    if (!isAdmin) return;
    setSelectedMember(member);
    setPanelOpen(true);
  }

  const innerProps: InnerMemberListProps = {
    isAdmin,
    onRowClick: handleRowClick,
    // Admins already hold the full roles; their names serve the badge too
    roleNames: roleNames ?? roles,
    refreshKey,
  };

  return (
    <>
      {AUTH_MODE === "mock" ? (
        <MockMemberList {...innerProps} />
      ) : (
        <KeycloakBffMemberList {...innerProps} />
      )}

      {isAdmin && (
        <MemberDetailPanel
          open={panelOpen}
          onOpenChange={setPanelOpen}
          member={selectedMember}
          roles={roles}
          slug={slug}
          onSaved={() => setRefreshKey((k) => k + 1)}
        />
      )}
    </>
  );
}
