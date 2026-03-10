"use client";

import { useState, useEffect, useCallback } from "react";
import { X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import { CAPABILITY_META } from "@/lib/capabilities";
import {
  assignMemberRole,
  fetchMemberCapabilities,
} from "@/app/(app)/org/[slug]/team/actions";
import type { OrgRole } from "@/lib/api/org-roles";

interface MemberInfo {
  id: string;
  name: string;
  email: string;
  role: string;
}

interface MemberDetailPanelProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  member: MemberInfo | null;
  roles: OrgRole[];
  slug: string;
}

export function MemberDetailPanel({
  open,
  onOpenChange,
  member,
  roles,
  slug,
}: MemberDetailPanelProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [isFetching, setIsFetching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedRoleId, setSelectedRoleId] = useState<string>("");
  const [overrides, setOverrides] = useState<string[]>([]);

  const systemRoles = roles.filter((r) => r.isSystem);
  const customRoles = roles.filter((r) => !r.isSystem);

  const selectedRole = roles.find((r) => r.id === selectedRoleId);
  const roleCapabilities = new Set(selectedRole?.capabilities ?? []);

  // Load member capabilities when panel opens
  const loadMemberCapabilities = useCallback(async () => {
    if (!member) return;
    setIsFetching(true);
    setError(null);
    try {
      const caps = await fetchMemberCapabilities(member.id);
      if (caps) {
        // Find the role that matches the member's current role
        const matchingRole = roles.find((r) => r.name === caps.roleName);
        if (matchingRole) {
          setSelectedRoleId(matchingRole.id);
        }
        setOverrides(caps.overrides);
      } else {
        // Fallback: try to match by system role
        const sysRole = roles.find(
          (r) => r.isSystem && `org:${r.slug}` === member.role,
        );
        if (sysRole) {
          setSelectedRoleId(sysRole.id);
        }
        setOverrides([]);
      }
    } finally {
      setIsFetching(false);
    }
  }, [member, roles]);

  useEffect(() => {
    if (open && member) {
      loadMemberCapabilities();
    }
  }, [open, member, loadMemberCapabilities]);

  function handleRoleChange(roleId: string) {
    setSelectedRoleId(roleId);
    // Reset overrides when role changes
    setOverrides([]);
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
        // Remove the minus (restore default)
        setOverrides((prev) => prev.filter((o) => o !== `-${cap}`));
      } else {
        // Add minus (remove from role)
        setOverrides((prev) => [...prev, `-${cap}`]);
      }
    } else {
      if (hasPlus) {
        // Remove the plus (restore default)
        setOverrides((prev) => prev.filter((o) => o !== `+${cap}`));
      } else {
        // Add plus (add beyond role)
        setOverrides((prev) => [...prev, `+${cap}`]);
      }
    }
  }

  async function handleSave() {
    if (!member || !selectedRoleId) return;
    setIsLoading(true);
    setError(null);
    try {
      const result = await assignMemberRole(
        slug,
        member.id,
        selectedRoleId,
        overrides,
      );
      if (result.success) {
        onOpenChange(false);
      } else {
        setError(result.error ?? "An error occurred.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsLoading(false);
    }
  }

  if (!member) return null;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        className="flex w-full flex-col gap-0 overflow-y-auto p-0 sm:max-w-md"
        showCloseButton={false}
        onPointerDownOutside={(e) => {
          const target = e.target as HTMLElement | null;
          if (!target?.closest("[data-slot='sheet-overlay']")) {
            e.preventDefault();
          }
        }}
      >
        <SheetTitle className="sr-only">Member Detail</SheetTitle>
        <SheetDescription className="sr-only">
          Member role and capability management.
        </SheetDescription>

        {/* Header */}
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-6 py-4 dark:border-slate-800">
          <div className="min-w-0 flex-1">
            <h2 className="text-base font-semibold leading-snug text-slate-950 dark:text-slate-50">
              {member.name}
            </h2>
            <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
              {member.email}
            </p>
          </div>
          <SheetClose asChild>
            <Button
              variant="ghost"
              size="icon"
              className="shrink-0"
              aria-label="Close"
            >
              <X className="size-4" />
            </Button>
          </SheetClose>
        </div>

        {error && (
          <div className="mx-6 mt-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
            {error}
          </div>
        )}

        {isFetching ? (
          <div className="px-6 py-8 text-center text-sm text-slate-500">
            Loading capabilities...
          </div>
        ) : (
          <>
            {/* Role section */}
            <div className="border-b border-slate-200 px-6 py-4 dark:border-slate-800">
              <Label className="mb-2 block text-sm font-medium">Role</Label>
              <Select
                value={selectedRoleId}
                onValueChange={handleRoleChange}
              >
                <SelectTrigger className="h-9 w-full">
                  <SelectValue placeholder="Select a role..." />
                </SelectTrigger>
                <SelectContent>
                  {systemRoles.length > 0 && (
                    <SelectGroup>
                      <SelectLabel>System</SelectLabel>
                      {systemRoles.map((role) => (
                        <SelectItem key={role.id} value={role.id}>
                          {role.name}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  )}
                  {customRoles.length > 0 && (
                    <SelectGroup>
                      <SelectLabel>Custom</SelectLabel>
                      {customRoles.map((role) => (
                        <SelectItem key={role.id} value={role.id}>
                          {role.name}
                        </SelectItem>
                      ))}
                    </SelectGroup>
                  )}
                </SelectContent>
              </Select>
            </div>

            {/* Capabilities section */}
            <div className="flex-1 px-6 py-4">
              <Label className="mb-3 block text-sm font-medium">
                Capabilities
              </Label>
              <div className="space-y-3">
                {CAPABILITY_META.map((cap) => {
                  const enabled = isEffectivelyEnabled(cap.value);
                  const status = getOverrideStatus(cap.value);

                  return (
                    <label
                      key={cap.value}
                      className="flex items-start gap-3 cursor-pointer"
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
                            <span className="text-xs font-medium text-destructive">
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
            </div>
          </>
        )}

        {/* Footer */}
        <div className="mt-auto flex flex-col gap-2 p-4">
          <Button
            onClick={handleSave}
            disabled={isLoading || isFetching || !selectedRoleId}
          >
            {isLoading ? "Saving..." : "Save"}
          </Button>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isLoading}
          >
            Cancel
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}
