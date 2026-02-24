"use client";

import { createContext, useEffect, useState, type ReactNode } from "react";
import { useAuth } from "@/hooks/use-auth";
import { publicFetch } from "@/lib/api-client";
import type { BrandingInfo } from "@/lib/types";

const DEFAULT_BRAND_COLOR = "#3B82F6";

export interface BrandingContextValue {
  orgName: string;
  logoUrl: string | null;
  brandColor: string;
  footerText: string | null;
  isLoading: boolean;
}

export const BrandingContext = createContext<BrandingContextValue | null>(null);

interface BrandingProviderProps {
  children: ReactNode;
}

export function BrandingProvider({ children }: BrandingProviderProps) {
  const { customer } = useAuth();
  const [branding, setBranding] = useState<BrandingInfo | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const orgId = customer?.orgId;
    if (!orgId) {
      setIsLoading(false);
      return;
    }

    let cancelled = false;

    async function fetchBranding() {
      try {
        const response = await publicFetch(
          `/portal/branding?orgId=${encodeURIComponent(orgId!)}`,
        );
        if (!cancelled && response.ok) {
          const data: BrandingInfo = await response.json();
          setBranding(data);
        }
      } catch {
        // Branding fetch failure is non-fatal -- use defaults
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    fetchBranding();

    return () => {
      cancelled = true;
    };
  }, [customer?.orgId]);

  const value: BrandingContextValue = {
    orgName: branding?.orgName ?? customer?.name ?? "",
    logoUrl: branding?.logoUrl ?? null,
    brandColor: branding?.brandColor ?? DEFAULT_BRAND_COLOR,
    footerText: branding?.footerText ?? null,
    isLoading,
  };

  return (
    <BrandingContext.Provider value={value}>{children}</BrandingContext.Provider>
  );
}
