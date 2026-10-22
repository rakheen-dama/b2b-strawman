import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { EmailSettingsContent } from "@/components/email/EmailSettingsContent";

/** Documents › Email tab (Phase 87 S9, ADR-341) — the former email settings page's loader. */
export async function renderEmailTab() {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Email</h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to manage email settings. Only admins and owners can access
          this page.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Email</h1>

      <EmailSettingsContent />
    </div>
  );
}
