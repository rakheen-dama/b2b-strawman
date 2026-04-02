"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { subscribe } from "@/app/(app)/org/[slug]/settings/billing/actions";

interface SubscribeButtonProps {
  disabled?: boolean;
  className?: string;
}

export function SubscribeButton({ disabled, className }: SubscribeButtonProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleClick() {
    setError(null);
    setIsLoading(true);

    try {
      const result = await subscribe();

      // Create a hidden form and POST to PayFast
      const form = document.createElement("form");
      form.method = "POST";
      form.action = result.paymentUrl;

      for (const [name, value] of Object.entries(result.formFields)) {
        const input = document.createElement("input");
        input.type = "hidden";
        input.name = name;
        input.value = value;
        form.appendChild(input);
      }

      document.body.appendChild(form);
      form.submit();
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to start subscription.";
      setError(message);
      setIsLoading(false);
    }
  }

  return (
    <div>
      <Button
        variant="accent"
        disabled={disabled || isLoading}
        onClick={handleClick}
        className={cn(className)}
      >
        {isLoading ? (
          <>
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            Subscribing...
          </>
        ) : (
          "Subscribe"
        )}
      </Button>
      {error && <p className="mt-2 text-sm text-destructive">{error}</p>}
    </div>
  );
}
