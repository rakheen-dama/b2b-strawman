export const MAX_FILE_SIZE = 100 * 1024 * 1024; // 100 MB
export const MAX_FILE_SIZE_LABEL = "100 MB";

export const ALLOWED_MIME_TYPES = new Set([
  // Documents
  "application/pdf",
  "application/msword",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.ms-excel",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "application/vnd.ms-powerpoint",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  "text/plain",
  "text/csv",
  // Images
  "image/jpeg",
  "image/png",
  "image/gif",
  "image/webp",
  "image/svg+xml",
  // Archives
  "application/zip",
  "application/gzip",
  "application/x-tar",
]);

const EXTENSION_TO_MIME: Record<string, string> = {
  pdf: "application/pdf",
  doc: "application/msword",
  docx: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  xls: "application/vnd.ms-excel",
  xlsx: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  ppt: "application/vnd.ms-powerpoint",
  pptx: "application/vnd.openxmlformats-officedocument.presentationml.presentation",
  txt: "text/plain",
  csv: "text/csv",
  jpg: "image/jpeg",
  jpeg: "image/jpeg",
  png: "image/png",
  gif: "image/gif",
  webp: "image/webp",
  svg: "image/svg+xml",
  zip: "application/zip",
  gz: "application/gzip",
  tar: "application/x-tar",
};

export const ACCEPT_ATTRIBUTE =
  ".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.jpg,.jpeg,.png,.gif,.webp,.svg,.zip,.gz,.tar";

interface ValidationResult {
  valid: boolean;
  error?: string;
  mimeType: string;
}

export function validateFile(file: File): ValidationResult {
  let mimeType = file.type;

  // Fallback: if browser didn't detect MIME type, infer from extension
  if (!mimeType) {
    const ext = file.name.split(".").pop()?.toLowerCase() ?? "";
    mimeType = EXTENSION_TO_MIME[ext] ?? "";
  }

  if (file.size === 0) {
    return { valid: false, error: "File is empty.", mimeType };
  }

  if (file.size > MAX_FILE_SIZE) {
    return {
      valid: false,
      error: `File exceeds the maximum size of ${MAX_FILE_SIZE_LABEL}.`,
      mimeType,
    };
  }

  if (!ALLOWED_MIME_TYPES.has(mimeType)) {
    const ext = file.name.split(".").pop() ?? "unknown";
    return {
      valid: false,
      error: `File type .${ext} is not supported.`,
      mimeType,
    };
  }

  return { valid: true, mimeType };
}
