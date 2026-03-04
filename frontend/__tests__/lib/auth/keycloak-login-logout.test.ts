import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// Stub NEXT_PUBLIC_GATEWAY_URL before importing the module
vi.stubEnv("NEXT_PUBLIC_GATEWAY_URL", "http://localhost:8443");

describe("Keycloak login/logout URLs", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it("login URL points to gateway OAuth2 authorization endpoint", async () => {
    const { getKeycloakLoginUrl } = await import(
      "@/components/auth/user-menu-bff"
    );

    expect(getKeycloakLoginUrl()).toBe(
      "http://localhost:8443/oauth2/authorization/keycloak",
    );
  });

  it("logout URL points to gateway logout endpoint", async () => {
    const { getKeycloakLogoutUrl } = await import(
      "@/components/auth/user-menu-bff"
    );

    expect(getKeycloakLogoutUrl()).toBe("http://localhost:8443/logout");
  });
});
