"use client";

import { useState, useCallback, useEffect, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { PortalHeader } from "@/components/portal/portal-header";

const TOKEN_KEY = "portal_token";
const CUSTOMER_NAME_KEY = "portal_customer_name";

export default function PortalLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [customerName, setCustomerName] = useState<string | undefined>();

  useEffect(() => {
    const name = sessionStorage.getItem(CUSTOMER_NAME_KEY);
    if (name) {
      setCustomerName(name);
    }
  }, []);

  const handleSignOut = useCallback(() => {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(CUSTOMER_NAME_KEY);
    router.push("/portal");
  }, [router]);

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950">
      <PortalHeader customerName={customerName} onSignOut={handleSignOut} />
      <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
        {children}
      </main>
    </div>
  );
}
