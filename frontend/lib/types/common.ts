// ---- Common types shared across domains ----

// ---- Tags (from TagController.java) ----

export interface TagResponse {
  id: string;
  name: string;
  slug: string;
  color: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTagRequest {
  name: string;
  color?: string | null;
}

export interface UpdateTagRequest {
  name: string;
  color?: string | null;
}

export interface SetEntityTagsRequest {
  tagIds: string[];
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

// ---- Completeness (from CompletenessScore.java, AggregatedCompletenessResponse.java) ----

export interface CompletenessScore {
  totalRequired: number;
  filled: number;
  percentage: number;
}

export interface MissingFieldSummary {
  fieldName: string;
  fieldSlug: string;
  customerCount: number;
}

export interface AggregatedCompletenessResponse {
  topMissingFields: MissingFieldSummary[];
  incompleteCount: number;
  totalCount: number;
}
