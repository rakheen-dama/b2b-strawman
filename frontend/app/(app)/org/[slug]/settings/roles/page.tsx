import Link from "next/link";
import { notFound } from "next/navigation";
import { ChevronLeft, Plus, Users } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { fetchOrgRoles } from "@/lib/api/org-roles";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { RoleCard } from "@/components/roles/role-card";
import { CAPABILITIES } from "@/lib/capabilities";
import type { OrgRole } from "@/lib/api/org-roles";

const ALL_CAPABILITIES = Object.values(CAPABILITIES);

export default async function RolesSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const capData = await fetchMyCapabilities();

  if (!capData.isAdmin && !capData.isOwner) {
    return notFound();
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
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Settings
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Roles & Permissions
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage system roles and create custom roles with granular permissions.
        </p>
      </div>

      {/* System Roles Section */}
      <div className="space-y-4">
        <div>
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            System Roles
          </h2>
          <p className="mt-0.5 text-sm text-slate-600 dark:text-slate-400">
            Built-in roles that cannot be modified or deleted.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {systemRoles.map((role) => {
            const displayCaps = getDisplayCapabilities(role);
            return (
              <div
                key={role.id}
                className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950"
              >
                <div className="flex items-center gap-2">
                  <h3 className="font-semibold text-slate-950 dark:text-slate-50">
                    {role.name}
                  </h3>
                  <Badge variant="neutral">System</Badge>
                </div>

                {displayCaps.length > 0 ? (
                  <div className="mt-3 flex flex-wrap gap-1.5">
                    {displayCaps.map((cap) => (
                      <Badge key={cap} variant="secondary">
                        {cap.replace(/_/g, " ")}
                      </Badge>
                    ))}
                  </div>
                ) : (
                  <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
                    No default capabilities
                  </p>
                )}

                <div className="mt-3 flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400">
                  <Users className="size-3.5" />
                  <span>
                    {role.memberCount}{" "}
                    {role.memberCount === 1 ? "member" : "members"}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Custom Roles Section */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
              Custom Roles
            </h2>
            <p className="mt-0.5 text-sm text-slate-600 dark:text-slate-400">
              Create roles with specific capabilities for your team.
            </p>
          </div>
          {/* Wired to create dialog in 319B */}
          <Button size="sm" disabled>
            <Plus className="mr-1.5 size-4" />
            New Role
          </Button>
        </div>

        {customRoles.length === 0 ? (
          <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-8 text-center dark:border-slate-700 dark:bg-slate-900">
            <p className="text-sm text-slate-600 dark:text-slate-400">
              No custom roles yet. Create one to assign granular permissions to
              team members.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {customRoles.map((role) => (
              <RoleCard key={role.id} role={role} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
