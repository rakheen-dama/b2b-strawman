import { describe, it, expect, vi, afterEach } from "vitest";
import { useMessage } from "@/src/messages";

describe("useMessage hook", () => {
  const originalNodeEnv = process.env.NODE_ENV;

  afterEach(() => {
    process.env.NODE_ENV = originalNodeEnv;
    vi.restoreAllMocks();
  });

  it("resolves a simple key from namespace", () => {
    const { t } = useMessage("errors");
    const result = t("api.forbidden");
    expect(result).toBeTypeOf("string");
    expect(result.length).toBeGreaterThan(0);
    expect(result).not.toBe("api.forbidden");
  });

  it("resolves a nested dot-path key", () => {
    const { t } = useMessage("empty-states");
    const result = t("projects.list.heading");
    expect(result).toBeTypeOf("string");
    expect(result.length).toBeGreaterThan(0);
    expect(result).not.toBe("projects.list.heading");
  });

  it("interpolates {{variable}} tokens", () => {
    const { t } = useMessage("errors");
    const result = t("validation.maxLength", { max: "100" });
    expect(result).toContain("100");
    expect(result).not.toContain("{{max}}");
  });

  it("interpolates multiple variables", () => {
    const { t } = useMessage("getting-started");
    const result = t("card.progress", { completed: "3", total: "6" });
    expect(result).toContain("3");
    expect(result).toContain("6");
    expect(result).not.toMatch(/\{\{\w+\}\}/);
  });

  it("returns raw code for missing key in production", () => {
    process.env.NODE_ENV = "production";
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    const { t } = useMessage("errors");
    const result = t("nonexistent.key.here");
    expect(result).toBe("nonexistent.key.here");
    expect(warnSpy).not.toHaveBeenCalled();
  });

  it("logs console.warn for missing key in development", () => {
    process.env.NODE_ENV = "development";
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    const { t } = useMessage("errors");
    t("nonexistent.key.here");
    expect(warnSpy).toHaveBeenCalledWith(
      "[useMessage] Missing key: errors.nonexistent.key.here",
    );
  });
});
