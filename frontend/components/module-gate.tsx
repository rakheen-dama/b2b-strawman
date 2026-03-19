"use client";

import { useOrgProfile } from "@/lib/org-profile";

interface ModuleGateProps {
  module: string;
  fallback?: React.ReactNode;
  children: React.ReactNode;
}

export function ModuleGate({
  module,
  fallback = null,
  children,
}: ModuleGateProps) {
  const { isModuleEnabled } = useOrgProfile();

  if (!isModuleEnabled(module)) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
