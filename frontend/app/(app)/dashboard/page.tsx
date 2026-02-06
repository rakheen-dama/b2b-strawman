import { currentUser } from "@clerk/nextjs/server";

export default async function DashboardPage() {
  const user = await currentUser();

  return (
    <div>
      <h1 className="text-2xl font-bold">Welcome{user?.firstName ? `, ${user.firstName}` : ""}!</h1>
      <p className="mt-2 text-neutral-600 dark:text-neutral-400">
        This is your DocTeams dashboard. Organization management and projects are coming soon.
      </p>
    </div>
  );
}
