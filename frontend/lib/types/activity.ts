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
