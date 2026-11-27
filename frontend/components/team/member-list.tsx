"use client";

import { Users } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { EmptyState } from "@/components/empty-state";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { useOrgMembers } from "@/lib/auth/client";
import { useState, useEffect, useMemo, type MouseEvent } from "react";
import { cn } from "@/lib/utils";
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
  const { members: orgMembers, isLoaded } = useOrgMembers();
  // Derived once per load: a new `data` identity reads as navigation to the DataTable.
  const members = useMemo(
    () =>
      orgMembers.map(
        (member): MemberRowData => ({
          id: member.id,
          name: member.name ?? "Unknown",
          email: member.email,
          role: member.role,
        })
      ),
    [orgMembers]
  );

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

  return (
    <MemberTable
      members={members}
      roleNames={roleNames}
      isAdmin={isAdmin}
      onRowClick={onRowClick}
    />
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

  const rows = useMemo(
    () =>
      members.map(
        (member): MemberRowData => ({
          id: member.id,
          name: member.name ?? "Unknown",
          email: member.email,
          role: member.role,
          orgRoleName: member.orgRoleName,
          capabilityOverridesCount: member.capabilityOverridesCount,
        })
      ),
    [members]
  );

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

  // A failed load with no rows shows only the alert, never the empty state: a failed request is
  // not an empty org.
  if (!rows.length && loadError) return loadError;

  return (
    <>
      {loadError}
      <MemberTable members={rows} roleNames={roleNames} isAdmin={isAdmin} onRowClick={onRowClick} />
    </>
  );
}

// --- Shared UI components ---

/**
 * A custom role's display name: the gateway's orgRoleName, else the org role whose slug matches the
 * member's "org:<slug>" role, else the bare slug — never the "org:" wire form (G89-F65).
 */
function customRoleLabel(member: MemberRowData, roleNames: RoleName[]): string {
  if (member.orgRoleName) return member.orgRoleName;
  const slug = member.role.startsWith("org:") ? member.role.slice(4) : member.role;
  return roleNames.find((r) => r.slug === slug)?.name ?? slug;
}

/** The admin name button's data attribute: the one delegated click handler reads it. */
const OPEN_ATTR = "data-member-open";

/** Clicks on these (or inside them) keep their own behaviour (the kit's `INTERACTIVE_SELECTOR`). */
const INTERACTIVE_SELECTOR =
  'a, button, input, select, textarea, label, [role="menu"], [role="menuitem"], [role="menuitemcheckbox"], [role="menuitemradio"], [data-row-click="ignore"]';

function buildColumns(isAdmin: boolean, roleNames: RoleName[]) {
  return defineColumns<MemberRowData>([
    {
      id: "member",
      enableHiding: false,
      meta: { label: "Member" },
      cell: ({ row }) => {
        const member = row.original;
        return (
          <div className="flex items-center gap-3">
            <AvatarCircle name={member.name} size={32} />
            {isAdmin ? (
              // No onClick: the wrapper's delegated handler opens the panel (Enter/Space click
              // bubbles), so the button is the row's keyboard path.
              <button
                type="button"
                data-member-open={member.id}
                className="text-foreground focus-visible:ring-ring rounded-sm text-left font-medium outline-none focus-visible:ring-2"
              >
                {member.name}
              </button>
            ) : (
              <span className="text-foreground font-medium">{member.name}</span>
            )}
          </div>
        );
      },
    },
    {
      id: "email",
      meta: {
        label: "Email",
        headerClassName: "w-[200px]",
        cellClassName: "text-muted-foreground",
      },
      cell: ({ row }) => row.original.email,
    },
    {
      id: "role",
      meta: { label: "Role", headerClassName: "w-[100px]" },
      cell: ({ row }) => {
        const member = row.original;
        const roleInfo = ROLE_BADGES[member.role];
        return (
          <div className="flex items-center gap-1.5">
            {roleInfo ? (
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
        );
      },
    },
  ]);
}

function MemberTable({
  members,
  roleNames,
  isAdmin = false,
  onRowClick,
}: {
  members: MemberRowData[];
  roleNames: RoleName[];
  isAdmin?: boolean;
  onRowClick: (member: MemberRowData) => void;
}) {
  const columns = useMemo(() => buildColumns(isAdmin, roleNames), [isAdmin, roleNames]);

  // Admins open a member's panel from anywhere on the row (668A task-table precedent): the kit has
  // no row-click callback, so one delegated handler reads the row's name button.
  function handleClick(event: MouseEvent<HTMLDivElement>) {
    if (!isAdmin || event.defaultPrevented || event.button !== 0) return;
    const target = event.target;
    if (!(target instanceof Element) || !event.currentTarget.contains(target)) return;
    const nameButton = target.closest(`[${OPEN_ATTR}]`);
    if (!nameButton && target.closest(INTERACTIVE_SELECTOR)) return;
    if (!nameButton && window.getSelection()?.toString()) return;
    const opener = nameButton ?? target.closest("tbody tr")?.querySelector(`[${OPEN_ATTR}]`);
    const memberId = opener?.getAttribute(OPEN_ATTR);
    const member = members.find((m) => m.id === memberId);
    if (member) onRowClick(member);
  }

  return (
    <div className={cn(isAdmin && "[&_tbody_tr]:cursor-pointer")} onClick={handleClick}>
      <DataTable
        columns={columns}
        data={members}
        getRowId={(member) => member.id}
        rowTestId={(member) => `member-row-${member.email}`}
        columnVisibility={false}
        stickyHeader={false}
        empty={
          <EmptyState
            variant="default"
            icon={Users}
            title="No members found"
            description="Organization members will appear here"
          />
        }
      />
    </div>
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
