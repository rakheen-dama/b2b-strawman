"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { User } from "lucide-react";
import { useAuth } from "@/hooks/use-auth";
import { portalGet } from "@/lib/api-client";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
} from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import type { PortalProfile } from "@/lib/types";

function ProfileSkeleton() {
  return (
    <Card>
      <CardHeader>
        <Skeleton className="h-6 w-40" />
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          <Skeleton className="h-4 w-60" />
          <Skeleton className="h-4 w-48" />
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-4 w-44" />
        </div>
      </CardContent>
    </Card>
  );
}

function formatRole(role: string): string {
  return role.charAt(0).toUpperCase() + role.slice(1).toLowerCase();
}

export default function ProfilePage() {
  const { jwt } = useAuth();
  const router = useRouter();
  const [profile, setProfile] = useState<PortalProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchProfile = useCallback(async () => {
    if (!jwt) {
      router.push("/login");
      return;
    }

    setError(null);
    setIsLoading(true);
    try {
      const data = await portalGet<PortalProfile>("/portal/me");
      setProfile(data);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to load profile";
      if (message === "Unauthorized") {
        router.push("/login");
      } else {
        setError(message);
      }
    } finally {
      setIsLoading(false);
    }
  }, [jwt, router]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (cancelled) return;
      await fetchProfile();
    })();
    return () => {
      cancelled = true;
    };
  }, [fetchProfile]);

  return (
    <div>
      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        Profile
      </h1>

      {isLoading && <ProfileSkeleton />}

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          <p className="mb-2">{error}</p>
          <button
            type="button"
            onClick={() => fetchProfile()}
            className="inline-flex min-h-11 items-center rounded-md bg-white px-3 py-1.5 text-sm font-medium text-red-700 ring-1 ring-red-200 hover:bg-red-100"
          >
            Try again
          </button>
        </div>
      )}

      {!isLoading && !error && profile && (
        <Card className="w-full max-w-lg">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <User className="size-5 text-slate-500" />
              Contact Information
            </CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="space-y-4">
              <div>
                <dt className="text-xs font-medium text-slate-500">Name</dt>
                <dd className="mt-1 text-sm text-slate-900">
                  {profile.displayName}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-slate-500">Email</dt>
                <dd className="mt-1 text-sm break-all text-slate-900">
                  {profile.email}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-slate-500">Role</dt>
                <dd className="mt-1">
                  <Badge variant="neutral">{formatRole(profile.role)}</Badge>
                </dd>
              </div>
              <div>
                <dt className="text-xs font-medium text-slate-500">
                  Customer
                </dt>
                <dd className="mt-1 text-sm text-slate-900">
                  {profile.customerName}
                </dd>
              </div>
            </dl>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
