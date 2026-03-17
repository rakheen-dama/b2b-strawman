"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { getPortalToken } from "@/lib/portal-api";

function subscribe(callback: () => void) {
  window.addEventListener("storage", callback);
  return () => window.removeEventListener("storage", callback);
}

function getSnapshot(): boolean {
  return !!getPortalToken();
}

function getServerSnapshot(): boolean {
  return false;
}

export function PortalAuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [mounted, setMounted] = useState(false);
  const isAuthenticated = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    if (mounted && !isAuthenticated) {
      router.replace("/portal");
    }
  }, [mounted, isAuthenticated, router]);

  if (!mounted || !isAuthenticated) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Loader2 className="size-8 animate-spin text-slate-400" />
      </div>
    );
  }

  return <>{children}</>;
}
