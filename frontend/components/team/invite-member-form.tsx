"use client";

import { useOrganization } from "@clerk/nextjs";
import { useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { inviteMember } from "@/app/(app)/org/[slug]/team/actions";

interface InviteMemberFormProps {
  maxMembers: number;
  currentMembers: number;
  planTier: string;
}

export function InviteMemberForm({ maxMembers, currentMembers, planTier }: InviteMemberFormProps) {
  const { organization, invitations } = useOrganization({
    invitations: {
      pageSize: 5,
      keepPreviousData: true,
    },
  });
  const [emailAddress, setEmailAddress] = useState("");
  const [role, setRole] = useState<"org:member" | "org:admin">("org:member");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  if (!organization) return null;

  const pendingInvitations = organization.pendingInvitationsCount ?? 0;
  const totalUsed = currentMembers + pendingInvitations;
  const isAtLimit = maxMembers > 0 && totalUsed >= maxMembers;
  const fillPercent = maxMembers > 0 ? Math.min((totalUsed / maxMembers) * 100, 100) : 0;
  const isPro = planTier === "DEDICATED";

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    const trimmedEmail = emailAddress.trim();
    if (!trimmedEmail) {
      setError("Email address is required.");
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await inviteMember(trimmedEmail, role);
      if (!result.success) {
        setError(result.error ?? "Failed to send invitation.");
        return;
      }
      await invitations?.revalidate?.();
      setEmailAddress("");
      setSuccess(`Invitation sent to ${trimmedEmail}.`);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to send invitation.";
      setError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* Form row */}
      {!isAtLimit && (
        <form onSubmit={handleSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1 space-y-1.5">
            <label htmlFor="invite-email" className="text-sm font-medium">
              Email address
            </label>
            <Input
              id="invite-email"
              type="email"
              placeholder="colleague@company.com"
              value={emailAddress}
              onChange={(e) => {
                setEmailAddress(e.target.value);
                setError(null);
                setSuccess(null);
              }}
              required
            />
          </div>
          <div className="space-y-1.5">
            <label htmlFor="invite-role" className="text-sm font-medium">
              Role
            </label>
            <select
              id="invite-role"
              value={role}
              onChange={(e) => setRole(e.target.value as "org:member" | "org:admin")}
              className="border-input bg-background h-9 rounded-md border px-3 text-sm shadow-xs focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-500"
            >
              <option value="org:member">Member</option>
              <option value="org:admin">Admin</option>
            </select>
          </div>
          <Button type="submit" disabled={isSubmitting} size="sm">
            {isSubmitting ? "Sending..." : "Send Invite"}
          </Button>
        </form>
      )}

      {/* At-limit message */}
      {isAtLimit && (
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Member limit reached.{" "}
          <Link
            href={`/org/${organization.slug}/settings/billing`}
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
