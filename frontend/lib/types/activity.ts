export interface ActivityItem {
  id: string;
  message: string;
  actorName: string;
  actorAvatarUrl: string | null;
  entityType: string;
  entityId: string;
  entityName: string;
  eventType: string;
  occurredAt: string;
  /** Audit `actor_type`: "USER" | "SYSTEM" | "PORTAL_CONTACT" | … (Phase 89 §14.3.21, G-B8). */
  actorType: string;
  /** Audit `source`: "API" | "INTERNAL" | "PORTAL" | … */
  source: string;
}

export interface ActivityResponse {
  content: ActivityItem[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}
