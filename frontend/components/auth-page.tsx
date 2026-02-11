import Link from "next/link";

interface AuthPageProps {
  heading: string;
  subtitle: string;
  children: React.ReactNode;
}

export function AuthPage({ heading, subtitle, children }: AuthPageProps) {
  return (
    <div className="flex min-h-screen">
      {/* Left panel — desktop only */}
      <div className="hidden flex-col bg-olive-100 md:flex md:w-[55%]">
        <div className="p-8">
          <Link href="/" className="font-display text-xl text-olive-900">
            DocTeams
          </Link>
        </div>
        <div className="flex flex-1 flex-col items-center justify-center px-12">
          <h1 className="font-display text-3xl text-olive-900">{heading}</h1>
          <p className="mt-3 text-olive-700">{subtitle}</p>
        </div>
      </div>

      {/* Right panel — always visible */}
      <div className="flex w-full flex-col items-center justify-center bg-white md:w-[45%]">
        {children}
      </div>
    </div>
  );
}
