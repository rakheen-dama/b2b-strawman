"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/hooks/use-auth";
import { getLastOrgId } from "@/lib/auth";
import {
  PortalContextProvider,
  useProfile,
} from "@/hooks/use-portal-context";
import { PortalTopbar } from "@/components/portal-topbar";
import {
  PortalSidebar,
  PortalSidebarMobile,
} from "@/components/portal-sidebar";
import { PortalFooter } from "@/components/portal-footer";
import { TerminologyProvider } from "@/lib/terminology";

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
  // GAP-L-65: thread the firm's vertical profile (e.g. "legal-za") into the
  // TerminologyProvider so portal pages render with the same vocabulary the
  // firm-side UI uses (e.g. "Fee Notes" instead of "Invoices"). The profile
  // is sourced from the existing PortalContext, which already fetches the
  // session context at provider mount -- no extra network call.
  const verticalProfile = useProfile();

  return (
    <TerminologyProvider verticalProfile={verticalProfile}>
      <div className="flex min-h-screen flex-col bg-slate-50">
        <PortalTopbar onHamburgerClick={() => setSidebarOpen(true)} />
        <div className="flex flex-1">
          <PortalSidebar />
          <PortalSidebarMobile
            open={sidebarOpen}
            onOpenChange={setSidebarOpen}
          />
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
    </TerminologyProvider>
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
      // GAP-L-66: preserve the deep-link target + last-known orgId so /login
      // can render branding and submit a valid magic-link request after
      // session expiry.
      const params = new URLSearchParams();
      if (pathname && pathname !== "/login") {
        params.set("redirectTo", pathname);
      }
      const lastOrg = getLastOrgId();
      if (lastOrg) {
        params.set("orgId", lastOrg);
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
