"use client";

import {
  createContext,
  createElement,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { useAuth } from "@/hooks/use-auth";
import { portalGet } from "@/lib/api-client";
import { isSafeImageUrl, isValidHexColor } from "@/lib/utils";

/**
 * Client-side representation of the backend `PortalSessionContextDto` returned by
 * `GET /portal/session/context` (delivered in slice 494A).
 */
export interface PortalSessionContext {
  tenantProfile: string | null;
  enabledModules: string[];
  terminologyKey: string;
  brandColor: string | null;
  orgName: string | null;
  logoUrl: string | null;
}

const DEFAULT_BRAND_COLOR = "#3B82F6";

interface PortalContextState {
  data: PortalSessionContext | null;
  isSettled: boolean;
}

const DEFAULT_STATE: PortalContextState = { data: null, isSettled: false };

const PortalSessionContextCtx =
  createContext<PortalContextState>(DEFAULT_STATE);

export function PortalContextProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const [state, setState] = useState<PortalContextState>(DEFAULT_STATE);

  useEffect(() => {
    if (!isAuthenticated) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await portalGet<PortalSessionContext>(
          "/portal/session/context",
        );
        if (!cancelled) setState({ data: res, isSettled: true });
      } catch {
        // Non-fatal — nav falls back to defaults when context is null.
        if (!cancelled) setState({ data: null, isSettled: true });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  return createElement(
    PortalSessionContextCtx.Provider,
    { value: state },
    children,
  );
}

export function usePortalContext(): PortalSessionContext | null {
  return useContext(PortalSessionContextCtx).data;
}

export function useProfile(): string | null {
  return useContext(PortalSessionContextCtx).data?.tenantProfile ?? null;
}

export function useModules(): string[] {
  return useContext(PortalSessionContextCtx).data?.enabledModules ?? [];
}

export function useTerminologyKey(): string {
  return useContext(PortalSessionContextCtx).data?.terminologyKey ?? "";
}

/**
 * Back-compat hook matching the shape of the legacy `BrandingContext`.
 * The 494A DTO does not expose `footerText`, so it is always `null` here
 * — `PortalFooter` renders harmlessly without it.
 */
export interface PortalBranding {
  orgName: string;
  logoUrl: string | null;
  brandColor: string;
  footerText: string | null;
  isLoading: boolean;
}

export function useBranding(): PortalBranding {
  const state = useContext(PortalSessionContextCtx);
  const ctx = state.data;
  const rawLogo = ctx?.logoUrl ?? null;
  const rawColor = ctx?.brandColor ?? null;
  return {
    orgName: ctx?.orgName ?? "",
    logoUrl: rawLogo && isSafeImageUrl(rawLogo) ? rawLogo : null,
    brandColor:
      rawColor && isValidHexColor(rawColor) ? rawColor : DEFAULT_BRAND_COLOR,
    footerText: null,
    isLoading: !state.isSettled,
  };
}
