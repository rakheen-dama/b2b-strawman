"use client";

import { useEffect, useState } from "react";
import { signIn } from "next-auth/react";
import { Button } from "@/components/ui/button";

export function KeycloakSignIn() {
  const [error, setError] = useState(false);

  useEffect(() => {
    signIn("keycloak", { callbackUrl: "/dashboard" }).catch(() =>
      setError(true),
    );
  }, []);

  if (error) {
    return (
      <div className="flex flex-col items-center gap-4 p-8">
        <p className="text-red-600 dark:text-red-400">
          Failed to redirect to sign in. Please try again.
        </p>
        <Button
          onClick={() => {
            setError(false);
            signIn("keycloak", { callbackUrl: "/dashboard" }).catch(() =>
              setError(true),
            );
          }}
        >
          Retry
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center gap-4 p-8">
      <p className="text-slate-600">Redirecting to sign in...</p>
    </div>
  );
}
