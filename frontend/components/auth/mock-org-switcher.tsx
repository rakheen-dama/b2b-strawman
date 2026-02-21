"use client";

import { useContext } from "react";
import { MockAuthContext } from "@/lib/auth/client/mock-context";
import { Badge } from "@/components/ui/badge";

export function MockOrgSwitcher() {
  const ctx = useContext(MockAuthContext);
  const orgSlug = ctx?.orgSlug ?? null;

  return (
    <div className="flex items-center gap-2">
      {orgSlug && (
        <span className="truncate font-mono text-xs uppercase tracking-wider text-teal-500/80">
          {orgSlug}
        </span>
      )}
      <Badge variant="neutral">E2E</Badge>
    </div>
  );
}
