"use client";

import { useState } from "react";
import { signIn } from "next-auth/react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

interface KeycloakCreateOrgFormProps {
  createOrgAction: (name: string) => Promise<{ slug: string; orgId: string }>;
}

export function KeycloakCreateOrgForm({
  createOrgAction,
}: KeycloakCreateOrgFormProps) {
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await createOrgAction(name);
      // Re-authenticate to pick up the new org in the JWT token
      await signIn("keycloak", {
        callbackUrl: `/org/${result.slug}/dashboard`,
      });
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to create organization",
      );
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="w-full max-w-sm space-y-6">
      <div className="space-y-2">
        <Label
          htmlFor="org-name"
          className="text-sm font-medium text-slate-700 dark:text-slate-300"
        >
          Organization name
        </Label>
        <Input
          id="org-name"
          type="text"
          placeholder="My Company"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
          maxLength={255}
          className="w-full"
          disabled={isSubmitting}
        />
      </div>

      {error && (
        <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
      )}

      <Button
        type="submit"
        disabled={isSubmitting || !name.trim()}
        className="w-full"
      >
        {isSubmitting ? "Creating..." : "Create Organization"}
      </Button>
    </form>
  );
}
