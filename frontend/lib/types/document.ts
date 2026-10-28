import type { TemplateEntityType } from "./template";

// ---- Documents (from DocumentController.java) ----

export type DocumentStatus = "PENDING" | "UPLOADED" | "FAILED";

export type DocumentScope = "ORG" | "PROJECT" | "CUSTOMER";

export type DocumentVisibility = "INTERNAL" | "SHARED" | "PORTAL";

/** Provenance of a document (Document.Source constants). COMMENT = attached through a comment. */
export type DocumentSource =
  | "MANUAL"
  | "AI_GENERATED"
  | "TEMPLATE_GENERATED"
  | "EMAIL_INGEST"
  | "COMMENT";

export interface Document {
  id: string;
  matterId: string | null;
  fileName: string;
  contentType: string;
  size: number;
  status: DocumentStatus;
  scope: DocumentScope;
  clientId: string | null;
  visibility: DocumentVisibility;
  source: DocumentSource;
  uploadedBy: string | null;
  uploadedByName: string | null;
  uploadedAt: string | null;
  createdAt: string;
}

export interface UploadInitRequest {
  fileName: string;
  contentType: string;
  size: number;
}

export interface UploadInitResponse {
  documentId: string;
  presignedUrl: string;
  expiresInSeconds: number;
}

export interface PresignDownloadResponse {
  presignedUrl: string;
  expiresInSeconds: number;
}

// ---- Generated Documents (from GeneratedDocumentController.java) ----

export interface GenerateDocumentResponse {
  id: string;
  fileName: string;
  fileSize: number;
  documentId: string;
  generatedAt: string;
}

export interface GeneratedDocumentListResponse {
  id: string;
  templateName: string;
  primaryEntityType?: TemplateEntityType;
  primaryEntityId?: string;
  fileName: string;
  fileSize: number;
  generatedByName: string;
  generatedAt: string;
  /** Linked Document id; null when the document was not saved to the documents list. */
  documentId?: string | null;
  outputFormat?: string;
  hasDocxDownload?: boolean;
  /** Linked Document visibility (ADR-350); null when not saved to the documents list. */
  visibility?: DocumentVisibility | null;
}

export interface GenerateDocxResult {
  id: string;
  templateId: string;
  templateName: string;
  outputFormat: string;
  fileName: string;
  downloadUrl: string;
  pdfDownloadUrl: string | null;
  fileSize: number;
  generatedAt: string;
  warnings: string[];
}
