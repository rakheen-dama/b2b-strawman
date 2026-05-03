"use client";

import { useState, useCallback } from "react";
import { Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { RequiresCapability, CAPABILITIES } from "@/lib/capabilities";
import {
  startSession,
  type SessionHandle,
  type ContextRef,
} from "@/lib/api/assistant-specialists";
import { SPECIALIST_STRINGS } from "@/components/assistant/specialist-strings";
import { SpecialistPanel } from "@/components/assistant/specialist-panel";

export interface SpecialistLauncherButtonProps {
  specialistId: string;
  /** Toolbar/surface identifier — e.g. `"INVOICE_DRAFT_TOOLBAR"`. */
  surface: string;
  contextRef: { entityType: string; entityId: string };
  initialPrompt?: string;
  /** Override the registry-provided label. */
  ctaLabel?: string;
}

function LauncherInner({
  specialistId,
  surface,
  contextRef,
  initialPrompt,
  ctaLabel,
}: SpecialistLauncherButtonProps) {
  const [open, setOpen] = useState(false);
  const [inFlight, setInFlight] = useState(false);
  const [sessionHandle, setSessionHandle] = useState<SessionHandle | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleClick = useCallback(async () => {
    if (inFlight) return;
    setInFlight(true);
    setError(null);
    try {
      const fullContextRef: ContextRef = {
        ...contextRef,
        currentPage:
          typeof window !== "undefined" ? window.location.pathname : undefined,
      };
      const handle = await startSession(specialistId, {
        contextRef: fullContextRef,
        initialPrompt,
        surface,
      });
      setSessionHandle(handle);
      setOpen(true);
    } catch (err) {
      console.error("[SpecialistLauncher] startSession failed", err);
      setError(SPECIALIST_STRINGS.errorStartingSession);
    } finally {
      setInFlight(false);
    }
  }, [inFlight, specialistId, surface, contextRef, initialPrompt]);

  const label = ctaLabel ?? SPECIALIST_STRINGS.defaultLauncherLabel;

  return (
    <>
      <Button
        variant="soft"
        size="sm"
        onClick={handleClick}
        disabled={inFlight}
        aria-label={label}
        data-specialist-id={specialistId}
      >
        <Sparkles className="size-4 text-teal-600" />
        <span>{label}</span>
      </Button>
      {error && (
        <span role="alert" className="ml-2 text-xs text-red-600">
          {error}
        </span>
      )}
      {sessionHandle && (
        <SpecialistPanel
          key={sessionHandle.sessionId}
          open={open}
          onOpenChange={(o) => {
            setOpen(o);
            // Drop the handle on close so the next launch mounts a fresh panel
            // rather than stacking a second instance over the existing one.
            if (!o) setSessionHandle(null);
          }}
          sessionHandle={sessionHandle}
          initialPrompt={initialPrompt}
          contextRef={contextRef}
        />
      )}
    </>
  );
}

export function SpecialistLauncherButton(props: SpecialistLauncherButtonProps) {
  return (
    <RequiresCapability cap={CAPABILITIES.AI_ASSISTANT_USE}>
      <LauncherInner {...props} />
    </RequiresCapability>
  );
}
