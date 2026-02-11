"use client";

import { PortalHeader } from "@/components/portal/portal-header";
import { PortalAuthGuard } from "@/components/portal/portal-auth-guard";

export default function PortalAuthenticatedLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <PortalAuthGuard>
      <div className="min-h-screen bg-olive-50 dark:bg-olive-950">
        <PortalHeader />
        <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
          {children}
        </main>
      </div>
    </PortalAuthGuard>
  );
}
