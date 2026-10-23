import "server-only";

import { api } from "@/lib/api/client";
import { getDebtors, type PaginatedDebtorsResponse } from "@/lib/api/collections";
import { getCurrentUserEmail } from "@/lib/auth";
import { rethrowOnExpiredSession } from "@/lib/auth/expiry-server";
import { findCurrentMemberId } from "@/lib/current-member";
import { settle } from "@/lib/settle";
import { loadTodaysDiary } from "@/app/(app)/org/[slug]/my-work/todays-diary";
import type { TodaysDiaryEvents } from "@/components/dashboard/todays-agenda";
import type { HomePlan, HomeWidget, WidgetResult } from "@/lib/dashboard-home";
import type {
  MatterHealth,
  CrossMatterActivityItem,
  UnbilledWipResponse,
  TrustPositionResponse,
  FicaOutstandingResponse,
} from "@/lib/dashboard-types";
import type { MyWorkTasksResponse, OrgMember } from "@/lib/types";

// Legal-native Home fan-out (Phase 87 614B, arch §12.5.3). Server-only, not a server action: the
// Home page is the only caller, and the plan it passes must come from the caller's own
// capabilities, never from a client.

/**
 * Everything the Home page renders, keyed by `HomeWidget` name — `absentWidgets` (dashboard-home)
 * relies on that to drop every `absent` WidgetResult without a per-widget list.
 */
export interface HomeWidgetData {
  /** Today's diary — always shown; `null` = could not load, `"unidentified"` = no member match. */
  diary: TodaysDiaryEvents;
  tasks: WidgetResult<MyWorkTasksResponse>;
  unbilledWip: WidgetResult<UnbilledWipResponse>;
  trustPosition: WidgetResult<TrustPositionResponse>;
  ficaOutstanding: WidgetResult<FicaOutstandingResponse>;
  debtors: WidgetResult<PaginatedDebtorsResponse>;
  /** `null` = could not load (kept widgets keep their error convention). */
  activity: CrossMatterActivityItem[] | null;
  /** `null` = could not load, or not in the plan. */
  matterHealth: MatterHealth[] | null;
}

/**
 * An ungated read whose widget shows its own error state on `null` (matter health, activity). Not
 * in the plan → `null` without a request; an expired session (401) → re-login; anything else →
 * `null`.
 */
async function readOrNull<T>(
  enabled: boolean,
  label: string,
  read: () => Promise<T>
): Promise<T | null> {
  if (!enabled) return null;
  try {
    return await read();
  } catch (error) {
    await rethrowOnExpiredSession(error);
    console.error(`Failed to load ${label}:`, error);
    return null;
  }
}

/** The signed-in user's member id (session email ↔ member list); `null` when unresolved. */
async function resolveCurrentMemberId(): Promise<string | null> {
  try {
    const [email, members] = await Promise.all([
      getCurrentUserEmail(),
      api.get<OrgMember[]>("/api/members"),
    ]);
    return findCurrentMemberId(email, members);
  } catch (error) {
    await rethrowOnExpiredSession(error);
    console.error("Failed to resolve current member:", error);
    return null;
  }
}

async function loadHomeDiary(plan: HomePlan, today: string): Promise<TodaysDiaryEvents> {
  if (plan.diaryScope === "firm") return loadTodaysDiary(today, null, { firmWide: true });
  return loadTodaysDiary(today, await resolveCurrentMemberId());
}

/**
 * Home's parallel reads — each one only when its gate in `plan` passes (§12.5.3). Diary is the
 * caller's own day for members (feeEarnerId = me) and the firm's day for admins and owners.
 */
export async function fetchHomeWidgets(plan: HomePlan, today: string): Promise<HomeWidgetData> {
  const [diary, tasks, unbilledWip, trustPosition, ficaOutstanding, debtors, activity, health] =
    await Promise.all([
      loadHomeDiary(plan, today),
      settle(plan.tasks, "my tasks", () => api.get<MyWorkTasksResponse>("/api/my-work/tasks")),
      settle(plan.unbilledWip, "unbilled WIP", () =>
        api.get<UnbilledWipResponse>("/api/dashboard/unbilled-wip")
      ),
      settle(plan.trustPosition, "trust position", () =>
        api.get<TrustPositionResponse>("/api/dashboard/trust-position")
      ),
      settle(plan.ficaOutstanding, "FICA outstanding", () =>
        api.get<FicaOutstandingResponse>("/api/dashboard/fica-outstanding?limit=5")
      ),
      settle(plan.debtors, "debtors", () => getDebtors({ size: 5 })),
      readOrNull(plan.activity, "recent activity", () =>
        api.get<CrossMatterActivityItem[]>("/api/dashboard/activity?limit=15")
      ),
      readOrNull(plan.matterHealth, "matter health", () =>
        api.get<MatterHealth[]>("/api/dashboard/matter-health")
      ),
    ]);

  // `satisfies` keeps every key a widget name — `absentWidgets` depends on it.
  return {
    diary,
    tasks,
    unbilledWip,
    trustPosition,
    ficaOutstanding,
    debtors,
    activity,
    matterHealth: health,
  } satisfies Partial<Record<HomeWidget, unknown>>;
}
