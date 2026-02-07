import { describe, it, expect } from "vitest";
import { formatFileSize } from "./format";

describe("formatFileSize", () => {
  it("returns '0 B' for zero bytes", () => {
    expect(formatFileSize(0)).toBe("0 B");
  });

  it("formats bytes without decimal for values >= 10", () => {
    expect(formatFileSize(500)).toBe("500 B");
  });

  it("formats kilobytes with one decimal for small values", () => {
    expect(formatFileSize(1024)).toBe("1.0 KB");
  });

  it("formats megabytes", () => {
    expect(formatFileSize(1.5 * 1024 * 1024)).toBe("1.5 MB");
  });

  it("rounds large values in a unit", () => {
    expect(formatFileSize(100 * 1024 * 1024)).toBe("100 MB");
  });

  it("formats gigabytes", () => {
    expect(formatFileSize(1024 * 1024 * 1024)).toBe("1.0 GB");
  });
});
