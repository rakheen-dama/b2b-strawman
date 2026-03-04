interface LayoutProps {
  title: string;
  children: React.ReactNode;
}

export function Layout({ title, children }: LayoutProps) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-12">
      <div className="w-full max-w-[420px]">
        {/* Logo */}
        <div className="mb-8 text-center">
          <span className="font-['Sora'] text-2xl font-semibold tracking-tight text-slate-950">
            DocTeams
          </span>
        </div>

        {/* Card */}
        <div className="rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
          <h1 className="mb-6 font-['Sora'] text-xl font-semibold text-slate-900">
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
