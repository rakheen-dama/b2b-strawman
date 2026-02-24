"use client";

import { useContext } from "react";
import {
  BrandingContext,
  type BrandingContextValue,
} from "@/components/branding-provider";

/**
 * Hook to access portal branding context.
 * Must be used within a BrandingProvider.
 */
export function useBranding(): BrandingContextValue {
  const context = useContext(BrandingContext);
  if (!context) {
    throw new Error("useBranding must be used within a BrandingProvider");
  }
  return context;
}
