"use server";

import { api } from "@/lib/api";
import type { Project, OrgMember } from "@/lib/types";
import type { CalendarFilters, CalendarResponse } from "./calendar-types";

export async function getCalendarItems(
  from: string,
  to: string,
  filters?: CalendarFilters
): Promise<CalendarResponse> {
  const params = new URLSearchParams();
  params.set("from", from);
  params.set("to", to);
  if (filters?.projectId) params.set("projectId", filters.projectId);
  if (filters?.type) params.set("type", filters.type);
  if (filters?.assigneeId) params.set("assigneeId", filters.assigneeId);
  if (filters?.overdue) params.set("overdue", "true");

  return api.get<CalendarResponse>(`/api/calendar?${params.toString()}`);
}

export async function getCalendarProjects(): Promise<
  { id: string; name: string }[]
> {
  const projects = await api.get<Project[]>("/api/projects");
  return projects.map((p) => ({ id: p.id, name: p.name }));
}

export async function getCalendarMembers(): Promise<
  { id: string; name: string }[]
> {
  const members = await api.get<OrgMember[]>("/api/members");
  return members.map((m) => ({ id: m.id, name: m.name }));
}
