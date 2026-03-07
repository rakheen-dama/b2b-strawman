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

  return <>{children}</>;
}
