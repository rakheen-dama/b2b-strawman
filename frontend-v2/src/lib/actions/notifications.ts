"use server";

import { api, ApiError } from "@/lib/api";

export interface Notification {
  id: string;
  type: string;
  title: string;
  body: string | null;
  referenceEntityType: string | null;
  referenceEntityId: string | null;
  referenceProjectId: string | null;
  isRead: boolean;
  createdAt: string;
}

export interface NotificationsResponse {
  content: Notification[];
  page: {
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
  };
}

export interface UnreadCountResponse {
  count: number;
}

export interface ActionResult {
  success: boolean;
  error?: string;
}

export interface NotificationPreference {
  notificationType: string;
  inAppEnabled: boolean;
  emailEnabled: boolean;
}

export interface PreferencesResponse {
  preferences: NotificationPreference[];
}

export async function fetchNotifications(
  unreadOnly?: boolean,
  page?: number
): Promise<NotificationsResponse> {
  const params = new URLSearchParams();
  if (unreadOnly) params.set("unreadOnly", "true");
  if (page !== undefined) params.set("page", String(page));
  params.set("size", "10");

  const query = params.toString();
  return api.get<NotificationsResponse>(
    `/api/notifications${query ? `?${query}` : ""}`
  );
}

export async function fetchUnreadCount(): Promise<UnreadCountResponse> {
  return api.get<UnreadCountResponse>("/api/notifications/unread-count");
}

export async function markNotificationRead(id: string): Promise<ActionResult> {
  try {
    await api.put(`/api/notifications/${id}/read`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  return { success: true };
}

export async function markAllNotificationsRead(): Promise<ActionResult> {
  try {
    await api.put("/api/notifications/read-all");
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  return { success: true };
}

export async function dismissNotification(id: string): Promise<ActionResult> {
  try {
    await api.delete(`/api/notifications/${id}`);
  } catch (error) {
    if (error instanceof ApiError) {
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }
  return { success: true };
}

export async function fetchNotificationPreferences(): Promise<PreferencesResponse> {
  return api.get<PreferencesResponse>("/api/notifications/preferences");
}

export async function updateNotificationPreferences(
  preferences: NotificationPreference[]
): Promise<PreferencesResponse> {
  return api.put<PreferencesResponse>("/api/notifications/preferences", {
    preferences,
  });
}
