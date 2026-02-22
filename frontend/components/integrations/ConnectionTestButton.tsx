"use client";

import { useState, useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { testConnectionAction } from "@/app/(app)/org/[slug]/settings/integrations/actions";
import type { IntegrationDomain } from "@/lib/types";

interface ConnectionTestButtonProps {
  slug: string;
  domain: IntegrationDomain;
  disabled?: boolean;
}

export function ConnectionTestButton({
  slug,
  domain,
  disabled,
}: ConnectionTestButtonProps) {
  const [status, setStatus] = useState<
    "idle" | "testing" | "success" | "error"
  >("idle");
  const [message, setMessage] = useState<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
  }, []);

  async function handleTest() {
    setStatus("testing");
    setMessage(null);
    if (timerRef.current) clearTimeout(timerRef.current);

    try {
      const result = await testConnectionAction(slug, domain);
      if (result.success && result.data) {
        if (result.data.success) {
          setStatus("success");
          setMessage("Connection successful");
          timerRef.current = setTimeout(() => {
            setStatus("idle");
            setMessage(null);
          }, 3000);
        } else {
          setStatus("error");
          setMessage(
            result.data.errorMessage ?? "Connection test failed.",
          );
        }
      } else {
        setStatus("error");
        setMessage(result.error ?? "Connection test failed.");
      }
    } catch {
      setStatus("error");
      setMessage("An unexpected error occurred.");
    }
  }

  return (
    <div className="flex items-center gap-3">
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={handleTest}
        disabled={disabled || status === "testing"}
      >
        {status === "testing" && (
          <Loader2 className="mr-2 size-4 animate-spin" />
        )}
        Test Connection
      </Button>
      {status === "success" && message && (
        <span className="flex items-center gap-1 text-sm text-emerald-600 dark:text-emerald-400">
          <CheckCircle2 className="size-4" />
          {message}
        </span>
      )}
      {status === "error" && message && (
        <span className="flex items-center gap-1 text-sm text-destructive">
          <XCircle className="size-4" />
          {message}
        </span>
      )}
    </div>
  );
}
