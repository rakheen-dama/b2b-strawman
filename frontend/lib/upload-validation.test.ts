import { describe, it, expect } from "vitest";
import { validateFile, MAX_FILE_SIZE, ALLOWED_MIME_TYPES } from "./upload-validation";

function createFile(name: string, size: number, type: string = ""): File {
  const content = new Uint8Array(Math.min(size, 1));
  const file = new File([content], name, { type });
  // File constructor doesn't let us set arbitrary sizes, so use defineProperty
  Object.defineProperty(file, "size", { value: size });
  return file;
}

describe("validateFile", () => {
  it("accepts a valid PDF file", () => {
    const file = createFile("report.pdf", 1024, "application/pdf");
    const result = validateFile(file);
    expect(result.valid).toBe(true);
    expect(result.mimeType).toBe("application/pdf");
  });

  it("accepts a valid JPEG image", () => {
    const result = validateFile(createFile("photo.jpg", 5000, "image/jpeg"));
    expect(result.valid).toBe(true);
    expect(result.mimeType).toBe("image/jpeg");
  });

  it("accepts a valid PNG image", () => {
    const result = validateFile(createFile("logo.png", 5000, "image/png"));
    expect(result.valid).toBe(true);
    expect(result.mimeType).toBe("image/png");
  });

  it("rejects an empty file (size = 0)", () => {
    const result = validateFile(createFile("empty.pdf", 0, "application/pdf"));
    expect(result.valid).toBe(false);
    expect(result.error).toBe("File is empty.");
  });

  it("rejects a file exceeding MAX_FILE_SIZE", () => {
    const result = validateFile(createFile("huge.pdf", MAX_FILE_SIZE + 1, "application/pdf"));
    expect(result.valid).toBe(false);
    expect(result.error).toContain("maximum size");
  });

  it("rejects an unsupported MIME type", () => {
    const result = validateFile(createFile("virus.exe", 1024, "application/x-msdownload"));
    expect(result.valid).toBe(false);
    expect(result.error).toBe("File type .exe is not supported.");
  });

  it("falls back to extension-based MIME lookup when browser type is empty", () => {
    const result = validateFile(createFile("data.csv", 512, ""));
    expect(result.valid).toBe(true);
    expect(result.mimeType).toBe("text/csv");
  });

  it("rejects unknown extension when browser MIME type is empty", () => {
    const result = validateFile(createFile("something.xyz", 512, ""));
    expect(result.valid).toBe(false);
    expect(result.error).toBe("File type .xyz is not supported.");
  });

  it("accepts all configured MIME types", () => {
    for (const mimeType of ALLOWED_MIME_TYPES) {
      const result = validateFile(createFile("test.bin", 100, mimeType));
      expect(result.valid).toBe(true);
    }
  });
});
