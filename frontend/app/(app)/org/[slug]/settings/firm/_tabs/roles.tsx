import { Users } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { fetchOrgRoles } from "@/lib/api/org-roles";
import { PermissionDenied } from "@/components/permission-denied";
import { Badge } from "@b2mash/ui/badge";
import { PageHeader } from "@b2mash/ui/page-header";
import { CAPABILITIES } from "@/lib/capabilities";
import { CustomRolesSection } from "@/components/roles/custom-roles-section";
import { CapabilityBadge } from "@/components/roles/role-card";
import { SettingsSection } from "@/components/settings/settings-section";
import { CapabilityReference } from "./capability-reference";
import type { OrgRole } from "@/lib/api/org-roles";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

const ALL_CAPABILITIES = Object.values(CAPABILITIES);

/** Firm › Roles tab (Phase 87 S9, ADR-341) — the former settings/roles page's loader. */
export async function renderRolesTab({ slug }: SettingsTabProps) {
  const capData = await fetchMyCapabilities();

  if (!capData.isAdmin && !capData.isOwner) {
    return (
      <PermissionDenied
        featureName="Roles & Permissions"
        dashboardHref={`/org/${slug}/dashboard`}
      />
    );
  }

  let roles: OrgRole[] = [];

  try {
    roles = await fetchOrgRoles();
  } catch (error) {
    // Non-fatal: show empty state on API failure
    console.error("Failed to fetch org roles:", error);
  }

  const systemRoles = roles.filter((r) => r.isSystem);
  const customRoles = roles.filter((r) => !r.isSystem);

  // For display: Owner and Admin bypass all capability checks,
  // so we show all capabilities. Member has none by default.
  function getDisplayCapabilities(role: OrgRole): string[] {
    const name = role.name.toLowerCase();
    if (name === "owner" || name === "admin") {
      return ALL_CAPABILITIES;
    }
    return role.capabilities;
  }

  return (
    <div className="space-y-8">
      <PageHeader
        title="Roles & Permissions"
        description="Manage system roles and create custom roles with granular permissions."
      />

      <SettingsSection
        title="System Roles"
        description="Built-in roles that cannot be modified or deleted."
      >
        {/* K-104: three columns only from xl — at 1024 the section is too narrow for the
            Member card's badges; min-w-0 lets each card (and its badges) shrink to the column. */}
        <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
          {systemRoles.map((role) => {
            const displayCaps = getDisplayCapabilities(role);
            return (
              <div key={role.id} data-slot="role-card" className="min-w-0 rounded-lg border p-6">
                <div className="flex items-center gap-2">
                  <h3 className="text-foreground font-semibold">{role.name}</h3>
                  <Badge variant="neutral">System</Badge>
                </div>

                {displayCaps.length > 0 ? (
                  <div className="mt-3 flex min-w-0 flex-wrap gap-1.5">
                    {displayCaps.map((cap) => (
                      <CapabilityBadge key={cap} cap={cap} />
                    ))}
                  </div>
                ) : (
                  <p className="text-muted-foreground mt-3 text-sm">No default capabilities</p>
                )}

                <div className="text-muted-foreground mt-3 flex items-center gap-1 text-xs">
                  <Users className="size-3.5" />
                  <span>
                    {role.memberCount} {role.memberCount === 1 ? "member" : "members"}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </SettingsSection>

      {/* Custom Roles Section */}
      <CustomRolesSection slug={slug} customRoles={customRoles} />

      {/* Capability Reference — untitled section: the trigger is its label (665A DA-17) */}
      <SettingsSection>
        <CapabilityReference />
      </SettingsSection>
    </div>
  );
}
