// ---- Documents (from DocumentController.java) ----

export type DocumentStatus = "PENDING" | "UPLOADED" | "FAILED";

export type DocumentScope = "ORG" | "PROJECT" | "CUSTOMER";

export type DocumentVisibility = "INTERNAL" | "SHARED" | "PORTAL";

export interface Document {
  id: string;
  matterId: string | null;
  fileName: string;
  contentType: string;
  size: number;
  status: DocumentStatus;
  scope: DocumentScope;
  customerId: string | null;
  visibility: DocumentVisibility;
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
  fileName: string;
  fileSize: number;
  generatedByName: string;
  generatedAt: string;
  outputFormat?: string;
  hasDocxDownload?: boolean;
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
