"use client";

import { useState, useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { CAPABILITY_META } from "@/lib/capabilities";
import {
  inviteMember,
  listInvitations,
} from "@/app/(app)/org/[slug]/team/invitation-actions";
import type { OrgRole } from "@/lib/api/org-roles";
import {
  inviteMemberSchema,
  type InviteMemberFormData,
} from "@/lib/schemas/invite-member";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";

interface InviteMemberFormProps {
  maxMembers: number;
  currentMembers: number;
  planTier: string;
  orgSlug: string;
  roles: OrgRole[];
}

function MockInviteMemberForm({
  maxMembers,
  currentMembers,
  planTier,
  orgSlug,
  roles,
}: InviteMemberFormProps) {
  return (
    <InviteFormUI
      maxMembers={maxMembers}
      currentMembers={currentMembers}
      pendingInvitations={0}
      planTier={planTier}
      orgSlug={orgSlug}
      roles={roles}
      onInviteSent={() => {}}
      ready={true}
    />
  );
}

function KeycloakBffInviteMemberForm({
  maxMembers,
  currentMembers,
  planTier,
  orgSlug,
  roles,
}: InviteMemberFormProps) {
  const [pendingCount, setPendingCount] = useState(0);

  useEffect(() => {
    listInvitations().then((invitations) => {
      setPendingCount(invitations.length);
    });
  }, []);

  const handleInviteSent = () => {
    // Refresh the pending count after a new invite
    listInvitations().then((invitations) => {
      setPendingCount(invitations.length);
    });
  };

  return (
    <InviteFormUI
      maxMembers={maxMembers}
      currentMembers={currentMembers}
      pendingInvitations={pendingCount}
      planTier={planTier}
      orgSlug={orgSlug}
      roles={roles}
      onInviteSent={handleInviteSent}
      ready={true}
    />
  );
}

function InviteFormUI({
  maxMembers,
  currentMembers,
  pendingInvitations,
  planTier,
  orgSlug,
  roles,
  onInviteSent,
  ready,
}: {
  maxMembers: number;
  currentMembers: number;
  pendingInvitations: number;
  planTier: string;
  orgSlug: string;
  roles: OrgRole[];
  onInviteSent: () => void;
  ready: boolean;
}) {
  const form = useForm<InviteMemberFormData>({
    resolver: zodResolver(inviteMemberSchema),
    defaultValues: {
      emailAddress: "",
    },
  });

  const [role, setRole] = useState<"org:member" | "org:admin">("org:member");
  const [selectedRoleId, setSelectedRoleId] = useState<string | undefined>(
    undefined,
  );
  const [overrides, setOverrides] = useState<string[]>([]);
  const [customizeOpen, setCustomizeOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  if (!ready) return null;

  const customRoles = roles.filter((r) => !r.isSystem);
  const selectedRole = selectedRoleId
    ? roles.find((r) => r.id === selectedRoleId)
    : undefined;
  const isCustomRole = selectedRole && !selectedRole.isSystem;
  const roleCapabilities = new Set(selectedRole?.capabilities ?? []);

  const totalUsed = currentMembers + pendingInvitations;
  const isAtLimit = maxMembers > 0 && totalUsed >= maxMembers;
  const fillPercent =
    maxMembers > 0 ? Math.min((totalUsed / maxMembers) * 100, 100) : 0;
  const isPro = planTier === "DEDICATED";

  function handleRoleSelectChange(value: string) {
    // Reset overrides and close customize section when role changes
    setOverrides([]);
    setCustomizeOpen(false);

    if (systemSelectValues.has(value)) {
      // System role selected — map to auth role, clear custom role
      setRole(value === SYSTEM_ADMIN_VALUE ? "org:admin" : "org:member");
      setSelectedRoleId(undefined);
    } else {
      // Custom role — auth role stays member, store orgRoleId
      setRole("org:member");
      setSelectedRoleId(value);
    }
  }

  function isEffectivelyEnabled(cap: string): boolean {
    if (overrides.includes(`-${cap}`)) return false;
    if (overrides.includes(`+${cap}`)) return true;
    return roleCapabilities.has(cap);
  }

  function getOverrideStatus(
    cap: string,
  ): "added" | "removed" | "default" {
    if (overrides.includes(`+${cap}`)) return "added";
    if (overrides.includes(`-${cap}`)) return "removed";
    return "default";
  }

  function toggleCapability(cap: string) {
    const inRole = roleCapabilities.has(cap);
    const hasPlus = overrides.includes(`+${cap}`);
    const hasMinus = overrides.includes(`-${cap}`);

    if (inRole) {
      if (hasMinus) {
        setOverrides((prev) => prev.filter((o) => o !== `-${cap}`));
      } else {
        setOverrides((prev) => [...prev, `-${cap}`]);
      }
    } else {
      if (hasPlus) {
        setOverrides((prev) => prev.filter((o) => o !== `+${cap}`));
      } else {
        setOverrides((prev) => [...prev, `+${cap}`]);
      }
    }
  }

  // Well-known select values for system roles (not derived from role IDs)
  const SYSTEM_MEMBER_VALUE = "system:member";
  const SYSTEM_ADMIN_VALUE = "system:admin";
  const systemSelectValues = new Set([SYSTEM_MEMBER_VALUE, SYSTEM_ADMIN_VALUE]);

  // Compute the select value from current state
  function getSelectValue(): string {
    if (selectedRoleId) return selectedRoleId;
    if (role === "org:admin") return SYSTEM_ADMIN_VALUE;
    return SYSTEM_MEMBER_VALUE;
  }

  const handleSubmit = async (values: InviteMemberFormData) => {
    setError(null);
    setSuccess(null);
    setIsSubmitting(true);

    try {
      const result = await inviteMember(
        values.emailAddress.trim(),
        role,
        selectedRoleId,
        overrides.length > 0 ? overrides : undefined,
      );
      if (!result.success) {
        setError(result.error ?? "Failed to send invitation.");
        return;
      }
      onInviteSent();
      form.reset();
      setSuccess(`Invitation sent to ${values.emailAddress.trim()}.`);
    } catch (err: unknown) {
      const message =
        err instanceof Error ? err.message : "Failed to send invitation.";
      setError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* Form row */}
      {!isAtLimit && (
        <form
          onSubmit={form.handleSubmit(handleSubmit)}
          className="flex flex-col gap-3 sm:flex-row sm:items-end"
        >
          <div className="flex-1 space-y-1.5">
            <label htmlFor="invite-email" className="text-sm font-medium">
              Email address
            </label>
            <Input
              id="invite-email"
              type="email"
              placeholder="colleague@company.com"
              {...form.register("emailAddress")}
              onChange={(e) => {
                form.setValue("emailAddress", e.target.value);
                form.clearErrors("emailAddress");
                setError(null);
                setSuccess(null);
              }}
            />
            {form.formState.errors.emailAddress && (
              <p className="text-destructive text-sm" role="alert">
                {form.formState.errors.emailAddress.message}
              </p>
            )}
          </div>
          <div className="space-y-1.5">
            <label htmlFor="invite-role" className="text-sm font-medium">
              Role
            </label>
            <Select
              value={getSelectValue()}
              onValueChange={handleRoleSelectChange}
            >
              <SelectTrigger className="h-9 w-full min-w-[140px]" id="invite-role">
                <SelectValue placeholder="Select a role..." />
              </SelectTrigger>
              <SelectContent>
                <SelectGroup>
                  <SelectLabel>System</SelectLabel>
                  <SelectItem value={SYSTEM_MEMBER_VALUE}>Member</SelectItem>
                  <SelectItem value={SYSTEM_ADMIN_VALUE}>Admin</SelectItem>
                </SelectGroup>
                {customRoles.length > 0 && (
                  <SelectGroup>
                    <SelectLabel>Custom</SelectLabel>
                    {customRoles.map((r) => (
                      <SelectItem key={r.id} value={r.id}>
                        {r.name}
                      </SelectItem>
                    ))}
                  </SelectGroup>
                )}
              </SelectContent>
            </Select>
          </div>
          <Button type="submit" disabled={isSubmitting} size="sm">
            {isSubmitting ? "Sending..." : "Send Invite"}
          </Button>
        </form>
      )}

      {/* Capability summary pills — only for custom roles */}
      {isCustomRole && selectedRole && (
        <div className="flex flex-wrap gap-1.5">
          {CAPABILITY_META.filter((cap) =>
            selectedRole.capabilities.includes(cap.value),
          ).map((cap) => (
            <Badge key={cap.value} variant="secondary">
              {cap.label}
            </Badge>
          ))}
        </div>
      )}

      {/* Customize for this user — only for custom roles */}
      {isCustomRole && (
        <Collapsible open={customizeOpen} onOpenChange={setCustomizeOpen}>
          <CollapsibleTrigger className="flex items-center gap-1 text-sm font-medium text-slate-700 hover:text-slate-900 dark:text-slate-300 dark:hover:text-slate-100">
            <ChevronRight
              className={`size-4 transition-transform ${customizeOpen ? "rotate-90" : ""}`}
            />
            Customize for this user
          </CollapsibleTrigger>
          <CollapsibleContent>
            <div className="mt-3 space-y-3">
              {CAPABILITY_META.map((cap) => {
                const enabled = isEffectivelyEnabled(cap.value);
                const status = getOverrideStatus(cap.value);

                return (
                  <label
                    key={cap.value}
                    className="flex cursor-pointer items-start gap-3"
                  >
                    <Checkbox
                      checked={enabled}
                      onCheckedChange={() => toggleCapability(cap.value)}
                      aria-label={cap.label}
                    />
                    <div className="flex-1 space-y-0.5">
                      <div className="flex items-center gap-1.5">
                        <span className="text-sm font-medium leading-none">
                          {cap.label}
                        </span>
                        {status === "added" && (
                          <span className="text-xs font-medium text-teal-600">
                            +
                          </span>
                        )}
                        {status === "removed" && (
                          <span className="text-destructive text-xs font-medium">
                            &minus;
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        {cap.description}
                      </p>
                    </div>
                  </label>
                );
              })}
            </div>
          </CollapsibleContent>
        </Collapsible>
      )}

      {/* At-limit message */}
      {isAtLimit && (
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Member limit reached.{" "}
          <Link
            href={`/org/${orgSlug}/settings/billing`}
            className="font-medium text-teal-600 underline underline-offset-4 hover:text-teal-500"
          >
            Upgrade
          </Link>{" "}
          to invite more members.
        </p>
      )}

      {/* Plan limit progress bar */}
      {maxMembers > 0 && (
        <div className="space-y-1.5">
          <p className="text-sm text-slate-600 dark:text-slate-400">
            {totalUsed} of {maxMembers} members
          </p>
          <div className="h-2 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800">
            <div
              className={`h-full rounded-full transition-all ${isPro ? "bg-teal-500" : "bg-slate-500"}`}
              style={{ width: `${fillPercent}%` }}
            />
          </div>
        </div>
      )}

      {/* Feedback messages */}
      {error && <p className="text-destructive text-sm">{error}</p>}
      {success && <p className="text-sm text-emerald-600">{success}</p>}
    </div>
  );
}

export function InviteMemberForm(props: InviteMemberFormProps) {
  if (AUTH_MODE === "mock") return <MockInviteMemberForm {...props} />;
  return <KeycloakBffInviteMemberForm {...props} />;
}
