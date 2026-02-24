"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/hooks/use-auth";
import { BrandingProvider } from "@/components/branding-provider";
import { PortalHeader } from "@/components/portal-header";
import { PortalFooter } from "@/components/portal-footer";
import { useBranding } from "@/hooks/use-branding";

// Track client-side mount without setState-in-effect.
let mounted = false;
const subscribe = () => () => {};
function getHasMounted() {
  mounted = true;
  return true;
}
function getServerMounted() {
  return false;
}

function AuthenticatedShell({ children }: { children: React.ReactNode }) {
  const { brandColor } = useBranding();

  return (
    <div
      className="flex min-h-screen flex-col bg-slate-50"
      style={{ "--portal-brand-color": brandColor } as React.CSSProperties}
    >
      <PortalHeader />
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 lg:px-8">
        {children}
      </main>
      <PortalFooter />
    </div>
  );
}

export default function AuthenticatedLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { isAuthenticated } = useAuth();
  const router = useRouter();
  const hasMounted = useSyncExternalStore(subscribe, getHasMounted, getServerMounted);

  useEffect(() => {
    if (hasMounted && !isAuthenticated) {
      router.push("/login");
    }
  }, [hasMounted, isAuthenticated, router]);

  if (!hasMounted || !isAuthenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-300 border-t-teal-600" />
      </div>
    );
  }

  return (
    <BrandingProvider>
      <AuthenticatedShell>{children}</AuthenticatedShell>
    </BrandingProvider>
  );
}
