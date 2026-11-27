import { notFound } from "next/navigation";
import { getSessionIdentity } from "@/lib/auth";
import { PlatformAdminNav } from "@/components/platform-admin/platform-admin-nav";

export const dynamic = "force-dynamic";

export default async function PlatformAdminLayout({ children }: { children: React.ReactNode }) {
  let groups: string[] = [];
  try {
    const identity = await getSessionIdentity();
    groups = identity.groups;
  } catch {
    notFound();
  }

  if (!groups.includes("platform-admins")) {
    notFound();
  }

  return (
    <div>
      <PlatformAdminNav />
      {/* The only <main> on these pages: (app)/layout.tsx is a fragment, and the root layout's
          "Skip to content" link targets #main-content (K-98). */}
      <main id="main-content">{children}</main>
    </div>
  );
}
