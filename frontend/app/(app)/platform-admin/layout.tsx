import { notFound } from "next/navigation";
import { getAuthContext } from "@/lib/auth";

export default async function PlatformAdminLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  let groups: string[] = [];
  try {
    const ctx = await getAuthContext();
    groups = ctx.groups;
  } catch {
    notFound();
  }

  if (!groups.includes("platform-admins")) {
    notFound();
  }

  return <>{children}</>;
}
