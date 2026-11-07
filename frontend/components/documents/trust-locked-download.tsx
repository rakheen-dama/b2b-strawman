"use client";

import type { ReactNode } from "react";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { useCapabilities } from "@/lib/capabilities";

/** The backend's 403 title for the same refusal (TrustDataVisibility.NOT_AVAILABLE_TITLE). */
export const TRUST_LOCKED_REASON = "Trust details are not available";

interface TrustLockedDownloadProps {
  /** The document prints trust money (backend `containsTrustData`). */
  containsTrustData?: boolean | null;
  /** Renders the download control; `locked` = render it disabled. */
  children: (locked: boolean) => ReactNode;
}

/**
 * G89-F52 / D19: a document that prints trust money stays listed, but only a member holding
 * VIEW_TRUST may download it. For anyone else the download control renders disabled with a
 * "Trust details are not available" tooltip. The server's 403 is the real guard — this only spares
 * the member a failing click. Capability only (not the trust module flag), as on the server.
 */
export function TrustLockedDownload({ containsTrustData, children }: TrustLockedDownloadProps) {
  if (!containsTrustData) {
    return <>{children(false)}</>;
  }
  return <TrustGate>{children}</TrustGate>;
}

function TrustGate({ children }: { children: (locked: boolean) => ReactNode }) {
  const { hasCapability } = useCapabilities();
  if (hasCapability("VIEW_TRUST")) {
    return <>{children(false)}</>;
  }
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          {/* A disabled button fires no pointer events — the span carries the tooltip. */}
          <span tabIndex={0} className="inline-flex" data-testid="trust-locked-download">
            {children(true)}
          </span>
        </TooltipTrigger>
        <TooltipContent>
          <p>{TRUST_LOCKED_REASON}</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
