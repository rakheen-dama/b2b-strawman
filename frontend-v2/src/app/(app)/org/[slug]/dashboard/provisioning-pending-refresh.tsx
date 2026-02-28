"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

/**
 * Auto-refreshes the page every 3 seconds while the org
 * is still being provisioned (403 / member-not-available).
 */
export function ProvisioningPendingRefresh() {
  const router = useRouter();

  useEffect(() => {
    const id = setInterval(() => router.refresh(), 3000);
    return () => clearInterval(id);
  }, [router]);

  return null;
}
