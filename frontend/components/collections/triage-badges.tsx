"use client";

import { Badge } from "@b2mash/ui/badge";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";

/**
 * Human labels for the deterministic §3.4 triage signals plus the advisor-contributed
 * TRUST_FUNDS_AVAILABLE (ADR-329). Unknown (future advisor) signals fall back to a
 * humanized form of the raw name.
 */
const SIGNAL_LABELS: Record<string, string> = {
  DRIFTING: "Drifting",
  SERIAL_LATE: "Serial late",
  GONE_QUIET: "Gone quiet",
  ESCALATED: "Escalated",
  TRUST_FUNDS_AVAILABLE: "Trust funds available",
};

type SignalBadgeVariant = "warning" | "neutral" | "destructive" | "lead";

/**
 * TRUST_FUNDS_AVAILABLE is deliberately teal ("lead") — informational and visually
 * distinct, never destructive-styled. Badge prominence is the ADR-329 mitigation for
 * "reminder approved despite trust funds".
 */
const SIGNAL_VARIANTS: Record<string, SignalBadgeVariant> = {
  DRIFTING: "warning",
  SERIAL_LATE: "neutral",
  GONE_QUIET: "warning",
  ESCALATED: "destructive",
  TRUST_FUNDS_AVAILABLE: "lead",
};

function signalLabel(signal: string): string {
  return (
    SIGNAL_LABELS[signal] ??
    signal
      .replace(/_/g, " ")
      .toLowerCase()
      .replace(/^\w/, (c) => c.toUpperCase())
  );
}

function signalVariant(signal: string): SignalBadgeVariant {
  return SIGNAL_VARIANTS[signal] ?? "warning";
}

export interface TriageBadgesProps {
  signals: string[];
  /**
   * Advisor detail strings by signal name (additive 592B API field), e.g.
   * { TRUST_FUNDS_AVAILABLE: "R 84 200,00 held in trust" }. Rendered VERBATIM in a
   * tooltip — the "held in trust" wording is an ADR-329 decision; never rephrase it.
   */
  signalDetails?: Record<string, string>;
}

export function TriageBadges({ signals, signalDetails }: TriageBadgesProps) {
  if (signals.length === 0) return null;
  return (
    <TooltipProvider>
      {signals.map((signal) => {
        const detail = signalDetails?.[signal];
        const badge = (
          <Badge variant={signalVariant(signal)} data-testid={`triage-badge-${signal}`}>
            {signalLabel(signal)}
          </Badge>
        );
        if (!detail) {
          return <span key={signal}>{badge}</span>;
        }
        return (
          <Tooltip key={signal}>
            <TooltipTrigger asChild>
              {/* tabIndex makes the trust-detail tooltip reachable by keyboard, not just hover
                  (Radix Tooltip opens on focus) — the ADR-329 detail must not be mouse-only. */}
              <span tabIndex={0} className="cursor-help">
                {badge}
              </span>
            </TooltipTrigger>
            <TooltipContent className="max-w-xs">{detail}</TooltipContent>
          </Tooltip>
        );
      })}
    </TooltipProvider>
  );
}
