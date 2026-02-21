"use client";

import { ClerkProvider } from "@clerk/nextjs";
import { MockAuthContextProvider } from "./mock-context";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

interface AuthProviderProps {
  children: React.ReactNode;
}

/**
 * Conditional auth provider wrapper.
 * Renders ClerkProvider in clerk mode, MockAuthContextProvider in mock mode.
 */
export function AuthProvider({ children }: AuthProviderProps) {
  if (AUTH_MODE === "mock") {
    return <MockAuthContextProvider>{children}</MockAuthContextProvider>;
  }

  return (
    <ClerkProvider appearance={{ cssLayerName: "clerk" }}>
      {children}
    </ClerkProvider>
  );
}
