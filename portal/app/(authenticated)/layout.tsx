"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
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
        <PortalSidebarMobile
          open={sidebarOpen}
          onOpenChange={setSidebarOpen}
        />
        <main
          id="main-content"
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
  const hasMounted = useSyncExternalStore(
    subscribe,
    getHasMounted,
    getServerMounted,
  );

  useEffect(() => {
    if (hasMounted && !isAuthenticated) {
      router.replace("/login");
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
    <PortalContextProvider>
      <AuthenticatedShell>{children}</AuthenticatedShell>
    </PortalContextProvider>
  );
}
