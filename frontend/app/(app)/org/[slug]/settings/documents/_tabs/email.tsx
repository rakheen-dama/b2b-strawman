import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { EmailSettingsContent } from "@/components/email/EmailSettingsContent";

/** Documents › Email tab (Phase 87 S9, ADR-341) — the former email settings page's loader. */
export async function renderEmailTab() {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <PageHeader title="Email" />
        <p className="text-muted-foreground">
          You do not have permission to manage email settings. Only admins and owners can access
          this page.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader title="Email" />

      <EmailSettingsContent />
    </div>
  );
}
