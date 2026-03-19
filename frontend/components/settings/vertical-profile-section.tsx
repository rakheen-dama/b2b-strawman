"use client";

import { useState } from "react";
import useSWR from "swr";
import { useRouter } from "next/navigation";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
  fetchProfiles,
  updateVerticalProfile,
} from "@/app/(app)/org/[slug]/settings/general/profile-actions";
import type { ProfileSummary } from "@/app/(app)/org/[slug]/settings/general/profile-actions";

interface VerticalProfileSectionProps {
  slug: string;
  currentProfile: string | null;
  isOwner: boolean;
}

export function VerticalProfileSection({
  slug,
  currentProfile,
  isOwner,
}: VerticalProfileSectionProps) {
  const router = useRouter();
  const [pendingProfile, setPendingProfile] = useState<string | null>(
    currentProfile,
  );
  const [dialogOpen, setDialogOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const {
    data: profiles,
    error: fetchError,
    isLoading,
  } = useSWR<ProfileSummary[]>(
    isOwner ? "vertical-profiles-list" : null,
    () => fetchProfiles(),
  );

  async function handleConfirm() {
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await updateVerticalProfile(slug, pendingProfile);
      if (result.success) {
        setDialogOpen(false);
        router.refresh();
      } else {
        setError(result.error ?? "Failed to update vertical profile.");
        setDialogOpen(false);
      }
    } catch {
      setError("An unexpected error occurred.");
      setDialogOpen(false);
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!isOwner) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Vertical Profile
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Current profile:{" "}
          <span className="font-medium text-slate-950 dark:text-slate-50">
            {currentProfile ?? "None"}
          </span>
        </p>
      </div>
    );
  }

  return (
    <>
      <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
        <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
          Vertical Profile
        </h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Select an industry profile to enable specialized modules and
          templates.
        </p>

        <div className="mt-4 flex items-center gap-3">
          {isLoading ? (
            <div className="h-9 w-48 animate-pulse rounded-md bg-slate-100 dark:bg-slate-800" />
          ) : fetchError ? (
            <p className="text-sm text-destructive">
              Failed to load profiles.
            </p>
          ) : (
            <Select
              value={pendingProfile ?? ""}
              onValueChange={(value) =>
                setPendingProfile(value === "" ? null : value)
              }
            >
              <SelectTrigger className="w-64">
                <SelectValue placeholder="Select a profile" />
              </SelectTrigger>
              <SelectContent>
                {profiles?.map((profile) => (
                  <SelectItem key={profile.id} value={profile.id}>
                    {profile.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}

          <Button
            disabled={
              pendingProfile === currentProfile || isSubmitting || isLoading
            }
            onClick={() => {
              setError(null);
              setDialogOpen(true);
            }}
          >
            Apply Profile
          </Button>
        </div>

        {error && <p className="mt-3 text-sm text-destructive">{error}</p>}
      </div>

      <AlertDialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Change Vertical Profile</AlertDialogTitle>
            <AlertDialogDescription>
              Changing your vertical profile will add new field definitions,
              templates, and enable additional modules. Your existing data will
              not be affected.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isSubmitting}>
              Cancel
            </AlertDialogCancel>
            <Button onClick={handleConfirm} disabled={isSubmitting}>
              {isSubmitting ? "Applying..." : "Confirm"}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
