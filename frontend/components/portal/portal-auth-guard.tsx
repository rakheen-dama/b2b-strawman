"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { getPortalToken } from "@/lib/portal-api";

// useSyncExternalStore to read localStorage without triggering
// the "setState in effect" lint rule.
function subscribe(callback: () => void) {
  window.addEventListener("storage", callback);
  return () => window.removeEventListener("storage", callback);
}

function getSnapshot(): boolean {
  return !!getPortalToken();
}

function getServerSnapshot(): boolean {
  // On server, assume not authenticated (will check on client)
  return false;
}

export function PortalAuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const isAuthenticated = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useEffect(() => {
    if (!isAuthenticated) {
      router.replace("/portal");
    }
  }, [isAuthenticated, router]);

  if (!isAuthenticated) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="size-8 animate-spin text-olive-400" />
      </div>
    );
  }

  return <>{children}</>;
}
