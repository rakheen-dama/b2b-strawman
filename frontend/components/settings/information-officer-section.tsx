"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { updateDataProtectionSettings } from "@/app/(app)/org/[slug]/settings/data-protection/actions";
import { informationOfficerSchema } from "@/lib/schemas/data-protection";

interface InformationOfficerSectionProps {
  slug: string;
  initialName: string | null;
  initialEmail: string | null;
}

export function InformationOfficerSection({
  slug,
  initialName,
  initialEmail,
}: InformationOfficerSectionProps) {
  const router = useRouter();
  const [name, setName] = useState(initialName ?? "");
  const [email, setEmail] = useState(initialEmail ?? "");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState(false);

  const hasChanges = name !== (initialName ?? "") || email !== (initialEmail ?? "");

  async function handleSave() {
    setError(null);
    setFieldErrors({});
    setSaved(false);

    const parsed = informationOfficerSchema.safeParse({
      informationOfficerName: name,
      informationOfficerEmail: email,
    });
    if (!parsed.success) {
      const errors: Record<string, string> = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as string;
        if (!errors[key]) errors[key] = issue.message;
      }
      setFieldErrors(errors);
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await updateDataProtectionSettings(slug, {
        informationOfficerName: name || null,
        informationOfficerEmail: email || null,
      });
      if (result.success) {
        setSaved(true);
        router.refresh();
      } else {
        setError(result.error ?? "Failed to save information officer details.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
        Information Officer
      </h2>
      <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
        The designated information officer responsible for data protection compliance. Required for
        DSAR handling.
      </p>

      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="officer-name">Name</Label>
          <Input
            id="officer-name"
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setSaved(false);
              setFieldErrors((prev) => {
                if (!prev.informationOfficerName) return prev;
                const next = { ...prev };
                delete next.informationOfficerName;
                return next;
              });
            }}
            placeholder="e.g. Jane Smith"
            disabled={isSubmitting}
          />
          {fieldErrors.informationOfficerName && (
            <p className="text-destructive text-sm">{fieldErrors.informationOfficerName}</p>
          )}
        </div>
        <div className="space-y-2">
          <Label htmlFor="officer-email">Email</Label>
          <Input
            id="officer-email"
            type="email"
            value={email}
            onChange={(e) => {
              setEmail(e.target.value);
              setSaved(false);
              setFieldErrors((prev) => {
                if (!prev.informationOfficerEmail) return prev;
                const next = { ...prev };
                delete next.informationOfficerEmail;
                return next;
              });
            }}
            placeholder="e.g. privacy@company.com"
            disabled={isSubmitting}
          />
          {fieldErrors.informationOfficerEmail && (
            <p className="text-destructive text-sm">{fieldErrors.informationOfficerEmail}</p>
          )}
        </div>
      </div>

      <div className="mt-4 flex items-center gap-3">
        <Button onClick={handleSave} disabled={!hasChanges || isSubmitting}>
          {isSubmitting ? "Saving..." : "Save"}
        </Button>
        {saved && (
          <span className="text-sm text-teal-600 dark:text-teal-400">Saved successfully.</span>
        )}
      </div>

      {error && <p className="text-destructive mt-3 text-sm">{error}</p>}
    </div>
  );
}
