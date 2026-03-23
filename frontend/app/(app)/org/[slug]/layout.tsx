import { redirect } from "next/navigation";
import { getAuthContext, getCurrentUserInfo } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getOrgSettings } from "@/lib/api/settings";
import { DesktopSidebar } from "@/components/desktop-sidebar";
import { MobileSidebar } from "@/components/mobile-sidebar";
import { Breadcrumbs } from "@/components/breadcrumbs";
import { PlanBadge } from "@/components/billing/plan-badge";
import { NotificationBell } from "@/components/notifications/notification-bell";
import { PageTransition } from "@/components/page-transition";
import { ErrorBoundary } from "@/components/error-boundary";
import { AuthHeaderControls } from "@/components/auth-header-controls";
import { CapabilityProvider } from "@/lib/capabilities";
import { TerminologyProvider } from "@/lib/terminology";
import { OrgProfileProvider } from "@/lib/org-profile";
import { CommandPaletteProvider } from "@/components/command-palette-provider";
import { RecentItemsProvider } from "@/components/recent-items-provider";
import { AssistantProvider } from "@/components/assistant/assistant-provider";
import { AssistantPanel } from "@/components/assistant/assistant-panel";
import { AssistantTrigger } from "@/components/assistant/assistant-trigger";

export default async function OrgLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  let orgSlug: string;
  let groups: string[] = [];
  try {
    const ctx = await getAuthContext();
    orgSlug = ctx.orgSlug;
    groups = ctx.groups;
  } catch {
    // No valid auth context (no token or missing org claims) — redirect to dashboard
    redirect("/dashboard");
  }

  if (orgSlug !== slug) {
    redirect(`/org/${orgSlug}/dashboard`);
  }

  const [capResult, settingsResult, userInfoResult] = await Promise.allSettled([
    fetchMyCapabilities(),
    getOrgSettings(),
    getCurrentUserInfo(),
  ]);

  let capData = {
    capabilities: [] as string[],
    role: "member",
    isAdmin: false,
    isOwner: false,
  };
  if (capResult.status === "fulfilled") {
    capData = capResult.value;
  } else {
    // Capabilities unavailable — degrade gracefully, backend still enforces access control
    console.error("Failed to fetch capabilities, falling back to empty:", capResult.reason);
  }

  let verticalProfile: string | null = null;
  let enabledModules: string[] = [];
  let terminologyNamespace: string | null = null;
  if (settingsResult.status === "fulfilled") {
    verticalProfile = settingsResult.value.verticalProfile ?? null;
    enabledModules = settingsResult.value.enabledModules ?? [];
    terminologyNamespace = settingsResult.value.terminologyNamespace ?? null;
  } else {
    // Settings unavailable — fall back to no terminology overrides
    console.error("Failed to fetch org settings for terminology:", settingsResult.reason);
  }

  const userInfo =
    userInfoResult.status === "fulfilled"
      ? userInfoResult.value
      : { name: null, email: null };

  // TODO: Add PRO tier gate when tier info is available in frontend context
  // Backend already enforces tier-based access control — this is defense-in-depth
  const aiEnabled =
    settingsResult.status === "fulfilled" &&
    settingsResult.value.aiEnabled === true;

  return (
    <CapabilityProvider
      capabilities={capData.capabilities}
      role={capData.role}
      isAdmin={capData.isAdmin}
      isOwner={capData.isOwner}
    >
      <OrgProfileProvider
        verticalProfile={verticalProfile}
        enabledModules={enabledModules}
        terminologyNamespace={terminologyNamespace}
      >
      <TerminologyProvider verticalProfile={verticalProfile}>
      <RecentItemsProvider>
        <CommandPaletteProvider slug={slug}>
        <AssistantProvider aiEnabled={aiEnabled}>
        <div className="flex min-h-screen">
          <DesktopSidebar slug={slug} groups={groups} userName={userInfo.name} userEmail={userInfo.email} />
          <div className="flex flex-1 flex-col">
            <header className="sticky top-0 z-30 flex h-14 items-center gap-4 border-b border-slate-200/60 bg-slate-100/80 px-4 backdrop-blur-md md:px-6 dark:border-slate-800/60 dark:bg-slate-950/90">
              <MobileSidebar slug={slug} groups={groups} userName={userInfo.name} userEmail={userInfo.email} />
              <Breadcrumbs slug={slug} />
              <div className="ml-auto flex items-center gap-3">
                <AuthHeaderControls />
                <PlanBadge />
                <NotificationBell orgSlug={slug} />
              </div>
            </header>
            <main id="main-content" className="flex-1 bg-background dark:bg-slate-950">
              <div className="mx-auto max-w-7xl px-6 py-6 lg:px-10">
                <ErrorBoundary>
                  <PageTransition>{children}</PageTransition>
                </ErrorBoundary>
              </div>
            </main>
          </div>
        </div>
        <AssistantPanel slug={slug} orgRole={capData.role} />
        <AssistantTrigger />
        </AssistantProvider>
        </CommandPaletteProvider>
      </RecentItemsProvider>
      </TerminologyProvider>
      </OrgProfileProvider>
    </CapabilityProvider>
  );
}
