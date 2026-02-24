"use client";

import { useEffect, useState } from "react";
import { User } from "lucide-react";
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
  const [profile, setProfile] = useState<PortalProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function fetchProfile() {
      try {
        const data = await portalGet<PortalProfile>("/portal/me");
        if (!cancelled) {
          setProfile(data);
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : "Failed to load profile",
          );
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    fetchProfile();

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div>
      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        Profile
      </h1>

      {isLoading && <ProfileSkeleton />}

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      )}

      {!isLoading && !error && profile && (
        <Card className="max-w-lg">
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
                <dd className="mt-1 text-sm text-slate-900">
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
