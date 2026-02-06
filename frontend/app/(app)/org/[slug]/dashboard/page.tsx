import { auth } from "@clerk/nextjs/server";

export default async function OrgDashboardPage() {
  const { orgSlug } = await auth();

  return (
    <div>
      <h1 className="text-2xl font-bold">Dashboard</h1>
      <p className="mt-2 text-neutral-600 dark:text-neutral-400">
        Welcome to your{" "}
        <span className="font-medium text-neutral-900 dark:text-neutral-100">{orgSlug}</span>{" "}
        workspace. Projects and documents are coming soon.
      </p>
    </div>
  );
}
