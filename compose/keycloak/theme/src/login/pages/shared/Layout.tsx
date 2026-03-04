interface LayoutProps {
  title: string;
  children: React.ReactNode;
}

export function Layout({ title, children }: LayoutProps) {
  return (
    <div
      className="flex min-h-screen items-center justify-center px-4 py-12"
      style={{ backgroundColor: "oklch(94% 0.008 260)" }}
    >
      <div className="w-full max-w-[420px]">
        {/* Logo */}
        <div className="mb-8 text-center">
          <span
            className="text-2xl tracking-tight"
            style={{ fontFamily: "'Sora', sans-serif", fontWeight: 600, color: "oklch(13.5% 0.006 258)" }}
          >
            DocTeams
          </span>
        </div>

        {/* Card */}
        <div className="rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
          <h1
            className="mb-6 text-xl font-semibold"
            style={{ fontFamily: "'Sora', sans-serif", color: "oklch(20.5% 0.011 260)" }}
          >
            {title}
          </h1>
          {children}
        </div>

        {/* Footer */}
        <p className="mt-8 text-center text-xs text-slate-400">
          &copy; {new Date().getFullYear()} DocTeams
        </p>
      </div>
    </div>
  );
}
