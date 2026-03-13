"use client";

import useSWR from "swr";
import { fetchUnreadCount } from "@/lib/actions/notifications";

const POLL_INTERVAL_MS = 30_000;

export function useNotificationPolling() {
  const { data, mutate } = useSWR(
    "notification-unread-count",
    () => fetchUnreadCount(),
    {
      refreshInterval: POLL_INTERVAL_MS,
      dedupingInterval: 2000,
      // Keep showing previous count while revalidating
      revalidateOnFocus: true,
    }
  );

  return {
    unreadCount: data?.count ?? 0,
    refetch: () => mutate(),
  };
}
