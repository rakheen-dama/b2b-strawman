import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { getAuthContext, getCurrentUserInfo } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getOrgSettings } from "@/lib/api/settings";
import { getSubscription } from "@/lib/actions/subscription";
import { AppSidebar } from "@/components/shell/app-sidebar";
import { AppHeader } from "@/components/shell/app-header";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { SubscriptionBanner } from "@/components/billing/subscription-banner";
import { ErrorBoundary } from "@/components/error-boundary";
import { CapabilityProvider } from "@/lib/capabilities";
import { SubscriptionProvider } from "@/lib/subscription-context";
import { OrgProfileProvider } from "@/lib/org-profile";
import { CommandPaletteProvider } from "@/components/command-palette-provider";
import { RecentItemsProvider } from "@/components/recent-items-provider";
import { BreadcrumbLabelProvider } from "@/components/breadcrumb-label-provider";
import type { BillingResponse } from "@/lib/internal-api";

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

  // The shadcn sidebar's collapse cookie (SIDEBAR_COOKIE_NAME in components/ui/sidebar.tsx), read
  // server-side so first paint has the right width. No cookie = expanded.
  const sidebarState = (await cookies()).get("sidebar_state")?.value;

  const [capResult, settingsResult, userInfoResult, billingResult] = await Promise.allSettled([
    fetchMyCapabilities(),
    getOrgSettings(),
    getCurrentUserInfo(),
    getSubscription(),
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
  let orgName: string | null = null;
  let brandColor: string | null = null;
  let logoUrl: string | null = null;
  if (settingsResult.status === "fulfilled") {
    verticalProfile = settingsResult.value.verticalProfile ?? null;
    enabledModules = settingsResult.value.enabledModules ?? [];
    terminologyNamespace = settingsResult.value.terminologyNamespace ?? null;
    orgName = settingsResult.value.orgName ?? null;
    brandColor = settingsResult.value.brandColor ?? null;
    logoUrl = settingsResult.value.logoUrl ?? null;
  } else {
    // Settings unavailable — fall back to defaults (no modules, no branding)
    console.error("Failed to fetch org settings, falling back to defaults:", settingsResult.reason);
  }

  const userInfo =
    userInfoResult.status === "fulfilled" ? userInfoResult.value : { name: null, email: null };

  const FALLBACK_BILLING: BillingResponse = {
    status: "ACTIVE",
    trialEndsAt: null,
    currentPeriodEnd: null,
    graceEndsAt: null,
    monthlyAmountCents: 0,
    currency: "ZAR",
    limits: { maxMembers: 5, currentMembers: 0 },
    billingMethod: "MANUAL",
    adminNote: null,
  };

  let billingData: BillingResponse = FALLBACK_BILLING;
  if (billingResult.status === "fulfilled") {
    billingData = billingResult.value;
  } else {
    // Billing unavailable — degrade gracefully, show as active so app doesn't break
    console.error("Failed to fetch billing data, falling back to active:", billingResult.reason);
  }

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
        <RecentItemsProvider>
          {/* Entity pages publish their name for the last crumb (Phase 88 628B); both AppHeader's
              <Breadcrumbs> and the page {children} sit inside it. */}
          <BreadcrumbLabelProvider>
            <CommandPaletteProvider slug={slug}>
              <SidebarProvider defaultOpen={sidebarState !== "false"}>
                <AppSidebar
                  slug={slug}
                  orgName={orgName}
                  groups={groups}
                  userName={userInfo.name}
                  userEmail={userInfo.email}
                  brandColor={brandColor}
                  logoUrl={logoUrl}
                />
                {/* min-w-0 (Phase 88 628B, G-D15): a flex item's default min-width is its content's
                    min-content width, so a wide table or tab strip used to widen this column and
                    the whole document (1440px viewport: 1519px) instead of scrolling in its own
                    box. */}
                <SidebarInset className="min-w-0">
                  <AppHeader slug={slug} orgName={orgName} />
                  <main id="main-content" className="bg-background min-w-0 flex-1">
                    <SubscriptionProvider billingResponse={billingData}>
                      <SubscriptionBanner billingResponse={billingData} slug={slug} />
                      <div className="mx-auto max-w-7xl px-4 py-6 md:px-6">
                        <ErrorBoundary>{children}</ErrorBoundary>
                      </div>
                    </SubscriptionProvider>
                  </main>
                </SidebarInset>
              </SidebarProvider>
            </CommandPaletteProvider>
          </BreadcrumbLabelProvider>
        </RecentItemsProvider>
      </OrgProfileProvider>
    </CapabilityProvider>
  );
}
