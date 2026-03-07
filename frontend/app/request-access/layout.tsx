import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Request Access",
  description: "Request access to DocTeams for your organisation",
};

export default function RequestAccessLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-12 dark:bg-slate-950">
      <div className="w-full max-w-lg">
        <div className="mb-8 text-center">
          <h1 className="font-display text-2xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
            DocTeams
          </h1>
        </div>
        {children}
      </div>
    </div>
  );
}
