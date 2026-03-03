"use client";

import { useEffect } from "react";
import { signIn } from "next-auth/react";

export function KeycloakSignIn() {
  useEffect(() => {
    signIn("keycloak", { callbackUrl: "/dashboard" });
  }, []);

  return (
    <div className="flex flex-col items-center gap-4 p-8">
      <p className="text-slate-600">Redirecting to sign in...</p>
    </div>
  );
}
