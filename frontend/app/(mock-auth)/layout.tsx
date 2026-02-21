export default function MockAuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-slate-50 dark:bg-slate-950">
      <div className="mb-8">
        <span className="font-display text-2xl text-slate-900 dark:text-slate-100">
          DocTeams
        </span>
      </div>
      {children}
    </div>
  );
}
