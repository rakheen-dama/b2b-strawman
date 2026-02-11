// ---- Projects (from ProjectController.java) ----

export interface Project {
  id: string;
  name: string;
  description: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  projectRole: ProjectRole | null;
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
}

export interface UpdateProjectRequest {
  name: string;
  description?: string;
}

// ---- Documents (from DocumentController.java) ----

export type DocumentStatus = "PENDING" | "UPLOADED" | "FAILED";

export interface Document {
  id: string;
  projectId: string;
  fileName: string;
  contentType: string;
  size: number;
  status: DocumentStatus;
  uploadedBy: string | null;
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

// ---- Members (from OrgMemberController.java) ----

export interface OrgMember {
  id: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  orgRole: string;
}

// ---- Project Members (from ProjectMemberController.java) ----

export type ProjectRole = "lead" | "member";

export interface ProjectMember {
  id: string;
  memberId: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  projectRole: ProjectRole;
  createdAt: string;
}

// ---- Customers (from CustomerController.java) ----

export type CustomerStatus = "ACTIVE" | "ARCHIVED";

export interface Customer {
  id: string;
  name: string;
  email: string;
  phone: string | null;
  idNumber: string | null;
  status: CustomerStatus;
  notes: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCustomerRequest {
  name: string;
  email: string;
  phone?: string;
  idNumber?: string;
  notes?: string;
}

export interface UpdateCustomerRequest {
  name: string;
  email: string;
  phone?: string;
  idNumber?: string;
  notes?: string;
}

export interface CustomerProject {
  customerId: string;
  projectId: string;
  linkedBy: string | null;
  createdAt: string;
}

// ---- Error (RFC 9457 ProblemDetail) ----

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  [key: string]: unknown;
}
