"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/hooks/use-auth";
import { PortalContextProvider } from "@/hooks/use-portal-context";
import { PortalTopbar } from "@/components/portal-topbar";
import {
  PortalSidebar,
  PortalSidebarMobile,
} from "@/components/portal-sidebar";
import { PortalFooter } from "@/components/portal-footer";

// Track client-side mount without setState-in-effect.
const subscribe = () => () => {};
function getHasMounted() {
  return true;
}
function getServerMounted() {
  return false;
}

function AuthenticatedShell({ children }: { children: React.ReactNode }) {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="flex min-h-screen flex-col bg-slate-50">
      <PortalTopbar onHamburgerClick={() => setSidebarOpen(true)} />
      <div className="flex flex-1">
        <PortalSidebar />
        <PortalSidebarMobile open={sidebarOpen} onOpenChange={setSidebarOpen} />
        <main
          id="main-content"
          tabIndex={-1}
          className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 lg:px-8"
        >
          {children}
        </main>
      </div>
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
  const pathname = usePathname();
  const hasMounted = useSyncExternalStore(
    subscribe,
    getHasMounted,
    getServerMounted,
  );

  useEffect(() => {
    if (hasMounted && !isAuthenticated) {
      // Phase 88 U11 (§13.6.6): every portal email link carries ?orgId=.
      // Forward it from the INCOMING URL so /login is branded and uses the
      // request-link fast path even in a fresh browser. The localStorage hint
      // is deliberately not forwarded: /login reads it for branding only.
      // The rest of the query (e.g. the digest's ?unsubscribe=1) stays in
      // redirectTo; orgId travels as its own param. window.location is read
      // in the effect because useSearchParams in a layout needs a Suspense
      // boundary.
      const incoming = new URLSearchParams(window.location.search);
      const orgId = incoming.get("orgId");
      incoming.delete("orgId");
      const params = new URLSearchParams();
      if (pathname && pathname !== "/login") {
        const rest = incoming.toString();
        params.set("redirectTo", rest ? `${pathname}?${rest}` : pathname);
      }
      if (orgId) {
        params.set("orgId", orgId);
      }
      const qs = params.toString();
      router.replace(qs ? `/login?${qs}` : "/login");
    }
  }, [hasMounted, isAuthenticated, router, pathname]);

  if (!hasMounted || !isAuthenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-slate-300 border-t-teal-600" />
      </div>
    );
  }

  return (
    <PortalContextProvider>
      <AuthenticatedShell>{children}</AuthenticatedShell>
    </PortalContextProvider>
  );
}
