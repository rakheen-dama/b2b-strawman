import Link from "next/link";
import { notFound } from "next/navigation";
import { getSessionIdentity } from "@/lib/auth";

export const dynamic = "force-dynamic";

export default async function PlatformAdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
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
      <nav className="border-b border-slate-200 bg-white px-6 py-3 flex gap-6">
        <Link
          href="/platform-admin/access-requests"
          className="text-sm font-medium text-slate-700 hover:text-slate-950"
        >
          Access Requests
        </Link>
        <Link
          href="/platform-admin/billing"
          className="text-sm font-medium text-slate-700 hover:text-slate-950"
        >
          Billing
        </Link>
        <Link
          href="/platform-admin/demo"
          className="text-sm font-medium text-slate-700 hover:text-slate-950"
        >
          Demo
        </Link>
      </nav>
      {children}
    </div>
  );
}
