import Link from "next/link";

interface AuthPageProps {
  heading: string;
  subtitle: string;
  children: React.ReactNode;
}

export function AuthPage({ heading, subtitle, children }: AuthPageProps) {
  return (
    <div className="flex min-h-screen">
      {/* Accessible heading for mobile (left panel is hidden below md) */}
      <h1 className="sr-only md:hidden">{heading}</h1>

      {/* Left panel -- desktop only */}
      <div className="hidden flex-col bg-slate-100 md:flex md:w-[55%] dark:bg-slate-900">
        <div className="p-8">
          <Link
            href="/"
            className="font-display text-xl text-slate-900 dark:text-slate-100"
          >
            DocTeams
          </Link>
        </div>
        <div className="flex flex-1 flex-col items-center justify-center px-12">
          <h1
            className="font-display text-3xl text-slate-900 dark:text-slate-100"
            aria-hidden="true"
          >
            {heading}
          </h1>
          <p className="mt-3 text-slate-700 dark:text-slate-300">{subtitle}</p>
        </div>
      </div>

      {/* Right panel -- always visible */}
      <div className="flex w-full flex-col items-center justify-center bg-white md:w-[45%] dark:bg-slate-950">
        {children}
      </div>
    </div>
  );
}
