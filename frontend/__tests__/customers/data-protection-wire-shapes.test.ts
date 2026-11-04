import { describe, it, expect } from "vitest";
// ADR-365 / R6.8: static, relative import — a missing or renamed JSON fails typecheck and vitest.
// Path: __tests__/customers → __tests__ → frontend → repo root.
import contract from "../../../backend/src/test/resources/wire/data-protection-contract.json";
import type {
  AnonymizationPreview,
  StandaloneAnonymizationResult,
  StandaloneExportResult,
} from "@/lib/types/data-protection";
// NOT lib/api/ai.ts — that file exports an unrelated AI-intake `ConflictMatch`.
import type { ConflictMatch } from "@/lib/types/legal";

const componentNames = (components: { name: string }[]) => components.map((c) => c.name);

// Each literal is typed Required<T>: a missing or extra key fails `pnpm typecheck`; the runtime
// assertion fails when the Java record changes and the literal was not updated. Literal key ORDER
// must equal the Java record's declaration order (toEqual on arrays is order-sensitive).
describe("data-protection wire shapes match the backend contract (ADR-365)", () => {
  it("pins exactly the four compliance records", () => {
    expect(Object.keys(contract).sort()).toEqual([
      "AnonymizationPreview",
      "AnonymizationResult",
      "ConflictDetail",
      "ExportStatusResponse",
    ]);
  });

  it("StandaloneExportResult ↔ ExportStatusResponse", () => {
    const literal: Required<StandaloneExportResult> = {
      exportId: "",
      status: "",
      downloadUrl: "",
      expiresAt: "",
      fileCount: 0,
      totalSizeBytes: 0,
      s3Key: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.ExportStatusResponse));
  });

  it("AnonymizationPreview ↔ AnonymizationPreview", () => {
    const literal: Required<AnonymizationPreview> = {
      clientId: "",
      clientName: "",
      portalContacts: 0,
      projects: 0,
      documents: 0,
      timeEntries: 0,
      invoices: 0,
      comments: 0,
      customFieldValues: 0,
      financialRecordsRetained: 0,
      financialRetentionExpiresAt: null,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.AnonymizationPreview));
  });

  it("StandaloneAnonymizationResult ↔ DataAnonymizationService.AnonymizationResult", () => {
    const literal: Required<StandaloneAnonymizationResult> = {
      customerAnonymized: false,
      documentsDeleted: 0,
      commentsRedacted: 0,
      portalContactsAnonymized: 0,
      invoicesPreserved: 0,
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.AnonymizationResult));
  });

  it("ConflictMatch ↔ ConflictCheckService.ConflictDetail", () => {
    // Java declaration order: customer* BEFORE project* (the TS interface lists project* first).
    const literal: Required<ConflictMatch> = {
      adversePartyId: null,
      adversePartyName: null,
      customerId: null,
      customerName: null,
      projectId: null,
      projectName: null,
      relationship: null,
      matchType: "",
      similarityScore: 0,
      explanation: "",
    };
    expect(Object.keys(literal)).toEqual(componentNames(contract.ConflictDetail));
  });
});
