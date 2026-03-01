import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, renderHook, act } from "@testing-library/react";
import { usePrerequisiteCheck } from "@/hooks/use-prerequisite-check";
import type { PrerequisiteCheck } from "@/components/prerequisite/types";

vi.mock("@/lib/actions/prerequisite-actions", () => ({
  checkPrerequisitesAction: vi.fn(),
}));

import { checkPrerequisitesAction } from "@/lib/actions/prerequisite-actions";

const mockAction = vi.mocked(checkPrerequisitesAction);

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  cleanup();
});

describe("usePrerequisiteCheck", () => {
  it("sets loading during runCheck then sets result", async () => {
    const result: PrerequisiteCheck = {
      passed: false,
      context: "INVOICE_GENERATION",
      violations: [
        {
          code: "MISSING_FIELD",
          message: "Missing address",
          entityType: "CUSTOMER",
          entityId: "cust-1",
          fieldSlug: "address",
          groupName: "Contact",
          resolution: "Add address",
        },
      ],
    };
    mockAction.mockResolvedValueOnce(result);

    const { result: hookResult } = renderHook(() =>
      usePrerequisiteCheck("INVOICE_GENERATION", "CUSTOMER", "cust-1"),
    );

    expect(hookResult.current.check).toBeNull();
    expect(hookResult.current.loading).toBe(false);
    expect(hookResult.current.error).toBeNull();

    await act(async () => {
      await hookResult.current.runCheck();
    });

    expect(hookResult.current.loading).toBe(false);
    expect(hookResult.current.check).toEqual(result);
    expect(hookResult.current.error).toBeNull();
  });

  it("returns passed check with no violations", async () => {
    const result: PrerequisiteCheck = {
      passed: true,
      context: "INVOICE_GENERATION",
      violations: [],
    };
    mockAction.mockResolvedValueOnce(result);

    const { result: hookResult } = renderHook(() =>
      usePrerequisiteCheck("INVOICE_GENERATION", "CUSTOMER", "cust-1"),
    );

    await act(async () => {
      await hookResult.current.runCheck();
    });

    expect(hookResult.current.check?.passed).toBe(true);
    expect(hookResult.current.check?.violations).toHaveLength(0);
  });

  it("clears check state on reset", async () => {
    const result: PrerequisiteCheck = {
      passed: false,
      context: "INVOICE_GENERATION",
      violations: [
        {
          code: "MISSING_FIELD",
          message: "Missing field",
          entityType: "CUSTOMER",
          entityId: "cust-1",
          fieldSlug: "phone",
          groupName: null,
          resolution: "Add phone",
        },
      ],
    };
    mockAction.mockResolvedValueOnce(result);

    const { result: hookResult } = renderHook(() =>
      usePrerequisiteCheck("INVOICE_GENERATION", "CUSTOMER", "cust-1"),
    );

    await act(async () => {
      await hookResult.current.runCheck();
    });

    expect(hookResult.current.check).not.toBeNull();

    act(() => {
      hookResult.current.reset();
    });

    expect(hookResult.current.check).toBeNull();
  });

  it("exposes error state when action rejects", async () => {
    mockAction.mockRejectedValueOnce(new Error("Network failure"));

    const { result: hookResult } = renderHook(() =>
      usePrerequisiteCheck("INVOICE_GENERATION", "CUSTOMER", "cust-1"),
    );

    await act(async () => {
      await hookResult.current.runCheck();
    });

    expect(hookResult.current.loading).toBe(false);
    expect(hookResult.current.check).toBeNull();
    expect(hookResult.current.error).toBeInstanceOf(Error);
    expect(hookResult.current.error?.message).toBe("Network failure");
  });

  it("clears error on reset", async () => {
    mockAction.mockRejectedValueOnce(new Error("Network failure"));

    const { result: hookResult } = renderHook(() =>
      usePrerequisiteCheck("INVOICE_GENERATION", "CUSTOMER", "cust-1"),
    );

    await act(async () => {
      await hookResult.current.runCheck();
    });

    expect(hookResult.current.error).not.toBeNull();

    act(() => {
      hookResult.current.reset();
    });

    expect(hookResult.current.error).toBeNull();
  });

  it("clears previous error on successful re-run", async () => {
    mockAction.mockRejectedValueOnce(new Error("Network failure"));

    const { result: hookResult } = renderHook(() =>
      usePrerequisiteCheck("INVOICE_GENERATION", "CUSTOMER", "cust-1"),
    );

    await act(async () => {
      await hookResult.current.runCheck();
    });

    expect(hookResult.current.error).not.toBeNull();

    const result: PrerequisiteCheck = {
      passed: true,
      context: "INVOICE_GENERATION",
      violations: [],
    };
    mockAction.mockResolvedValueOnce(result);

    await act(async () => {
      await hookResult.current.runCheck();
    });

    expect(hookResult.current.error).toBeNull();
    expect(hookResult.current.check?.passed).toBe(true);
  });
});
