"use client";

import { useOrganization } from "@clerk/nextjs";
import { useState } from "react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { inviteMember } from "@/app/(app)/org/[slug]/team/actions";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

interface InviteMemberFormProps {
  maxMembers: number;
  currentMembers: number;
  planTier: string;
  orgSlug: string;
}

function ClerkInviteMemberForm({
  maxMembers,
  currentMembers,
  planTier,
  orgSlug,
}: InviteMemberFormProps) {
  const { organization, invitations } = useOrganization({
    invitations: { pageSize: 5, keepPreviousData: true },
  });
  const pendingInvitations = organization?.pendingInvitationsCount ?? 0;

  return (
    <InviteFormUI
      maxMembers={maxMembers}
      currentMembers={currentMembers}
      pendingInvitations={pendingInvitations}
      planTier={planTier}
      orgSlug={orgSlug}
      onInviteSent={() => invitations?.revalidate?.()}
      ready={!!organization}
    />
  );
}

function MockInviteMemberForm(props: InviteMemberFormProps) {
  return (
    <InviteFormUI
      maxMembers={props.maxMembers}
      currentMembers={props.currentMembers}
      pendingInvitations={0}
      planTier={props.planTier}
      orgSlug={props.orgSlug}
      onInviteSent={() => {}}
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
  onInviteSent,
  ready,
}: {
  maxMembers: number;
  currentMembers: number;
  pendingInvitations: number;
  planTier: string;
  orgSlug: string;
  onInviteSent: () => void;
  ready: boolean;
}) {
  const [emailAddress, setEmailAddress] = useState("");
  const [role, setRole] = useState<"org:member" | "org:admin">("org:member");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  if (!ready) return null;

  const totalUsed = currentMembers + pendingInvitations;
  const isAtLimit = maxMembers > 0 && totalUsed >= maxMembers;
  const fillPercent =
    maxMembers > 0 ? Math.min((totalUsed / maxMembers) * 100, 100) : 0;
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
      onInviteSent();
      setEmailAddress("");
      setSuccess(`Invitation sent to ${trimmedEmail}.`);
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
      {!isAtLimit && (
        <form
          onSubmit={handleSubmit}
          className="flex flex-col gap-3 sm:flex-row sm:items-end"
        >
          <div className="flex-1 space-y-1.5">
            <label
              htmlFor="invite-email"
              className="text-sm font-medium text-slate-700"
            >
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
            <label
              htmlFor="invite-role"
              className="text-sm font-medium text-slate-700"
            >
              Role
            </label>
            <select
              id="invite-role"
              value={role}
              onChange={(e) =>
                setRole(e.target.value as "org:member" | "org:admin")
              }
              className="border-input bg-background flex h-9 rounded-md border px-3 text-sm shadow-xs focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-500"
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

      {isAtLimit && (
        <p className="text-sm text-slate-500">
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

      {maxMembers > 0 && (
        <div className="space-y-1.5">
          <p className="text-sm text-slate-500">
            {totalUsed} of {maxMembers} members
          </p>
          <div className="h-2 w-full overflow-hidden rounded-full bg-slate-200">
            <div
              className={`h-full rounded-full transition-all ${isPro ? "bg-teal-500" : "bg-slate-500"}`}
              style={{ width: `${fillPercent}%` }}
            />
          </div>
        </div>
      )}

      {error && <p className="text-sm text-red-600">{error}</p>}
      {success && <p className="text-sm text-emerald-600">{success}</p>}
    </div>
  );
}

export function InviteMemberForm(props: InviteMemberFormProps) {
  if (AUTH_MODE === "mock") return <MockInviteMemberForm {...props} />;
  return <ClerkInviteMemberForm {...props} />;
}
