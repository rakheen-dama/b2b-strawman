"use client";

import { useOrganization } from "@clerk/nextjs";
import { useState } from "react";
import Link from "next/link";
import { Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { inviteMember } from "@/app/(app)/org/[slug]/team/actions";

interface InviteMemberFormProps {
  maxMembers: number;
  currentMembers: number;
}

export function InviteMemberForm({ maxMembers, currentMembers }: InviteMemberFormProps) {
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
  const isAtLimit =
    maxMembers > 0 && currentMembers + pendingInvitations >= maxMembers;

  if (isAtLimit) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Sparkles className="h-4 w-4 shrink-0" />
        <p>
          Your organization has reached its member limit ({maxMembers}).{" "}
          <Link
            href={`/org/${organization.slug}/settings/billing`}
            className="font-medium text-primary underline underline-offset-4 hover:text-primary/80"
          >
            Upgrade your plan
          </Link>{" "}
          to invite more members.
        </p>
      </div>
    );
  }

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
    <div className="space-y-2">
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
            className="border-input bg-background h-9 rounded-md border px-3 text-sm shadow-xs"
          >
            <option value="org:member">Member</option>
            <option value="org:admin">Admin</option>
          </select>
        </div>
        <Button type="submit" disabled={isSubmitting} size="sm">
          {isSubmitting ? "Sending..." : "Send invite"}
        </Button>
      </form>
      {error && <p className="text-destructive text-sm">{error}</p>}
      {success && <p className="text-sm text-emerald-600">{success}</p>}
    </div>
  );
}
