import Link from "next/link";

export default function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-gradient-to-b from-white to-neutral-50 dark:from-neutral-950 dark:to-neutral-900">
      <div className="container max-w-4xl px-4 text-center">
        <h1 className="mb-4 text-5xl font-bold tracking-tight text-neutral-900 dark:text-neutral-50">
          DocTeams
        </h1>
        <p className="mb-8 text-xl text-neutral-600 dark:text-neutral-400">
          Multi-tenant document management for teams
        </p>
        <div className="flex flex-col gap-4 sm:flex-row sm:justify-center">
          <Link
            href="/sign-up"
            className="rounded-lg bg-neutral-900 px-6 py-3 font-medium text-white transition-colors hover:bg-neutral-700 dark:bg-neutral-50 dark:text-neutral-900 dark:hover:bg-neutral-200"
          >
            Get Started
          </Link>
          <Link
            href="/sign-in"
            className="rounded-lg border border-neutral-300 px-6 py-3 font-medium text-neutral-900 transition-colors hover:bg-neutral-100 dark:border-neutral-700 dark:text-neutral-50 dark:hover:bg-neutral-800"
          >
            Sign In
          </Link>
        </div>
      </div>
    </div>
  );
}
