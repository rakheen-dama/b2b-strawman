import Link from "next/link";

/**
 * No-app-chrome layout for the (auth) route group. Centered, slate background,
 * Kazi wordmark. No authenticated data fetch — these routes are public per the
 * middleware allowlist. Mirrors the (mock-auth) layout precedent.
 */
export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-slate-50 dark:bg-slate-950">
      <div className="mb-8">
        <Link href="/" className="font-display text-2xl text-slate-900 dark:text-slate-100">
          Kazi
        </Link>
      </div>
      {children}
    </div>
  );
}
