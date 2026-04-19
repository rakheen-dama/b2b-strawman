import { describe, it, expect } from "vitest";
import { isAllowedKcUrl } from "./validate";

describe("isAllowedKcUrl", () => {
  describe("accepted URL shapes", () => {
    it("accepts a KC login-actions action-token URL", () => {
      const url =
        "http://localhost:8180/realms/docteams/login-actions/action-token?key=eyJhbGci.abc&client_id=gateway-bff";
      expect(isAllowedKcUrl(url)).toBe(true);
    });

    it("accepts a KC openid-connect registrations URL (invite-user endpoint)", () => {
      const url =
        "http://localhost:8180/realms/docteams/protocol/openid-connect/registrations?client_id=gateway-bff&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2F&response_type=code&scope=openid";
      expect(isAllowedKcUrl(url)).toBe(true);
    });
  });

  describe("rejected URLs", () => {
    it("rejects an unlisted KC path under the correct origin", () => {
      const url = "http://localhost:8180/realms/docteams/other/something?key=abc";
      expect(isAllowedKcUrl(url)).toBe(false);
    });

    it("rejects a non-KC origin that contains the allow-listed path as a substring", () => {
      const url =
        "http://evil.com/?next=http://localhost:8180/realms/docteams/login-actions/action-token?key=abc";
      expect(isAllowedKcUrl(url)).toBe(false);
    });

    it("rejects a non-KC origin entirely", () => {
      expect(isAllowedKcUrl("http://evil.com/phishing")).toBe(false);
    });

    it("rejects an empty string", () => {
      expect(isAllowedKcUrl("")).toBe(false);
    });

    it("rejects null", () => {
      expect(isAllowedKcUrl(null)).toBe(false);
    });

    it("rejects undefined", () => {
      expect(isAllowedKcUrl(undefined)).toBe(false);
    });

    it("rejects URLs with embedded control characters", () => {
      const url =
        "http://localhost:8180/realms/docteams/login-actions/action-token?key=abc\u0000injected";
      expect(isAllowedKcUrl(url)).toBe(false);
    });

    it("rejects a protocol path without the query-string marker", () => {
      // The registrations allow-list entry ends in `?` on purpose — a URL that
      // stops at `/registrations` (no query) is not a real KC invite URL.
      const url = "http://localhost:8180/realms/docteams/protocol/openid-connect/registrations";
      expect(isAllowedKcUrl(url)).toBe(false);
    });
  });
});
