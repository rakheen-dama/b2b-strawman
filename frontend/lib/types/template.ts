// ---- Document Templates (from DocumentTemplateController.java) ----

export type TemplateCategory =
  | "ENGAGEMENT_LETTER"
  | "STATEMENT_OF_WORK"
  | "COVER_LETTER"
  | "PROJECT_SUMMARY"
  | "NDA";

export type TemplateEntityType = "PROJECT" | "CUSTOMER" | "INVOICE";

export type TemplateSource = "PLATFORM" | "ORG_CUSTOM";

export type TemplateFormat = "TIPTAP" | "DOCX";

export type OutputFormat = "PDF" | "DOCX" | "BOTH";

export interface DiscoveredField {
  path: string;
  status: "VALID" | "UNKNOWN";
  label: string | null;
}

export interface TemplateListResponse {
  id: string;
  name: string;
  slug: string;
  description: string;
  category: TemplateCategory;
  primaryEntityType: TemplateEntityType;
  source: TemplateSource;
  sourceTemplateId: string | null;
  active: boolean;
  sortOrder: number;
  format: TemplateFormat;
  docxFileName: string | null;
  docxFileSize: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface TemplateDetailResponse {
  id: string;
  name: string;
  slug: string;
  description: string;
  category: TemplateCategory;
  primaryEntityType: TemplateEntityType;
  content: Record<string, unknown>;
  legacyContent: string | null;
  css: string | null;
  source: TemplateSource;
  sourceTemplateId: string | null;
  packId: string | null;
  packTemplateKey: string | null;
  requiredContextFields?: Array<{ entity: string; field: string }> | null;
  format: TemplateFormat;
  docxFileName: string | null;
  docxFileSize: number | null;
  discoveredFields: DiscoveredField[] | null;
  active: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTemplateRequest {
  name: string;
  description?: string;
  category: TemplateCategory;
  primaryEntityType: TemplateEntityType;
  content: Record<string, unknown>;
  css?: string;
  slug?: string;
  requiredContextFields?: Array<{ entity: string; field: string }>;
}

export interface UpdateTemplateRequest {
  name: string;
  description?: string;
  content: Record<string, unknown>;
  css?: string;
  sortOrder?: number;
  requiredContextFields?: Array<{ entity: string; field: string }> | null;
}

export interface FieldValidationResult {
  entity: string;
  field: string;
  present: boolean;
  reason: string | null;
}

export interface TemplateValidationResult {
  allPresent: boolean;
  fields: FieldValidationResult[];
}

export interface PreviewResponse {
  html: string;
  validationResult?: TemplateValidationResult | null;
}
