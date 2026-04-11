"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { User, Loader2, AlertCircle } from "lucide-react";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { portalApi, PortalApiError, clearPortalAuth } from "@/lib/portal-api";
import type { PortalProfile } from "@/lib/types";

export default function PortalProfilePage() {
  const router = useRouter();
  const [profile, setProfile] = useState<PortalProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchProfile() {
      try {
        const data = await portalApi.get<PortalProfile>("/portal/me");
        setProfile(data);
      } catch (err) {
        if (err instanceof PortalApiError && err.status === 401) {
          clearPortalAuth();
          router.replace("/portal");
          return;
        }
        setError(err instanceof Error ? err.message : "Failed to load profile");
      } finally {
        setIsLoading(false);
      }
    }
    fetchProfile();
  }, [router]);

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-8 animate-spin text-slate-400" />
      </div>
    );
  }

  if (error) {
    return (
      <div
        className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
        role="alert"
      >
        <AlertCircle className="size-4 shrink-0" />
        {error}
      </div>
    );
  }

  if (!profile) return null;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-3">
        <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">Profile</h1>
      </div>

      {/* Profile Card */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <User className="size-5 text-slate-500" />
            {profile.displayName}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-4 sm:grid-cols-2">
            <div>
              <dt className="text-sm font-medium text-slate-500 dark:text-slate-400">Email</dt>
              <dd className="mt-1 text-sm text-slate-900 dark:text-slate-100">{profile.email}</dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-slate-500 dark:text-slate-400">Customer</dt>
              <dd className="mt-1 text-sm text-slate-900 dark:text-slate-100">
                {profile.customerName}
              </dd>
            </div>
            <div>
              <dt className="text-sm font-medium text-slate-500 dark:text-slate-400">Role</dt>
              <dd className="mt-1 text-sm text-slate-900 capitalize dark:text-slate-100">
                {profile.role.toLowerCase()}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </div>
  );
}
