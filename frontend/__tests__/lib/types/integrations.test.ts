import { describe, it, expect } from "vitest";
import type {
  IntegrationDomain,
  OrgIntegration,
  ConnectionTestResult,
} from "@/lib/types";

describe("Integration types -- compile-time type assertions", () => {
  it("IntegrationDomain accepts all valid domain values", () => {
    const accounting: IntegrationDomain = "ACCOUNTING";
    const ai: IntegrationDomain = "AI";
    const docSigning: IntegrationDomain = "DOCUMENT_SIGNING";
    const payment: IntegrationDomain = "PAYMENT";

    expect(accounting).toBe("ACCOUNTING");
    expect(ai).toBe("AI");
    expect(docSigning).toBe("DOCUMENT_SIGNING");
    expect(payment).toBe("PAYMENT");
  });

  it("OrgIntegration has all fields with correct nullable types", () => {
    const configured = {
      domain: "ACCOUNTING",
      providerSlug: "xero",
      enabled: true,
      keySuffix: "ab12",
      configJson: '{"region":"ZA"}',
      updatedAt: "2026-02-22T10:00:00Z",
    } satisfies OrgIntegration;

    expect(configured.domain).toBe("ACCOUNTING");
    expect(configured.providerSlug).toBe("xero");
    expect(configured.enabled).toBe(true);
    expect(configured.keySuffix).toBe("ab12");

    // Unconfigured integration has null fields
    const unconfigured = {
      domain: "AI",
      providerSlug: null,
      enabled: false,
      keySuffix: null,
      configJson: null,
      updatedAt: null,
    } satisfies OrgIntegration;

    expect(unconfigured.providerSlug).toBeNull();
    expect(unconfigured.enabled).toBe(false);
  });

  it("ConnectionTestResult has success, providerName, errorMessage fields", () => {
    const success = {
      success: true,
      providerName: "Xero",
      errorMessage: null,
    } satisfies ConnectionTestResult;

    expect(success.success).toBe(true);
    expect(success.providerName).toBe("Xero");
    expect(success.errorMessage).toBeNull();

    const failure = {
      success: false,
      providerName: "OpenAI",
      errorMessage: "Invalid API key",
    } satisfies ConnectionTestResult;

    expect(failure.success).toBe(false);
    expect(failure.errorMessage).toBe("Invalid API key");
  });
});
