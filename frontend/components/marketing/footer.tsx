import Link from "next/link";

const linkGroups = [
  {
    heading: "Product",
    links: [
      { label: "Features", href: "#features" },
      { label: "Pricing", href: "#pricing" },
    ],
  },
  {
    heading: "Company",
    links: [
      { label: "About", href: "#" },
      { label: "Contact", href: "mailto:hello@kazi.africa" },
    ],
  },
  {
    heading: "Legal",
    links: [
      { label: "Privacy", href: "#" },
      { label: "Terms", href: "#" },
    ],
  },
];

export function Footer() {
  return (
    <footer className="border-t border-white/5 bg-slate-950">
      <div className="mx-auto max-w-7xl px-6 py-12">
        {/* Top row */}
        <div className="flex flex-col gap-10 lg:flex-row lg:justify-between">
          {/* Logo & tagline */}
          <div>
            <Link href="/" className="font-display text-xl tracking-tight text-white">
              kazi
            </Link>
            <p className="mt-2 text-sm text-white/40">Practice management, built for Africa.</p>
          </div>

          {/* Link groups */}
          <div className="grid grid-cols-2 gap-8 md:grid-cols-3">
            {linkGroups.map((group) => (
              <div key={group.heading}>
                <h3 className="mb-3 text-sm font-medium text-white">{group.heading}</h3>
                <ul className="space-y-2">
                  {group.links.map((link) => (
                    <li key={link.label}>
                      {link.href.startsWith("mailto:") || link.href === "#" ? (
                        <a
                          href={link.href}
                          className="text-sm text-white/60 transition-colors hover:text-white"
                        >
                          {link.label}
                        </a>
                      ) : (
                        <Link
                          href={link.href}
                          className="text-sm text-white/60 transition-colors hover:text-white"
                        >
                          {link.label}
                        </Link>
                      )}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>

        {/* Bottom row */}
        <div className="mt-8 flex flex-col items-start justify-between gap-4 border-t border-white/10 pt-8 sm:flex-row sm:items-center">
          <p className="text-sm text-white/40">
            &copy; {new Date().getFullYear()} Kazi. All rights reserved.
          </p>
          <p className="text-sm text-white/30">Built in South Africa</p>
        </div>
      </div>
    </footer>
  );
}
