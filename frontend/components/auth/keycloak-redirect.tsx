"use client";

import { useEffect } from "react";

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443";

export function KeycloakRedirect() {
  useEffect(() => {
    window.location.href = `${GATEWAY_URL}/oauth2/authorization/keycloak`;
  }, []);

  return (
    <div className="flex min-h-screen items-center justify-center">
      <p className="text-slate-500">Redirecting to login...</p>
    </div>
  );
}
