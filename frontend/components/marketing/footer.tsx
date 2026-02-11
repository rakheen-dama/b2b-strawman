import Link from "next/link";

const linkGroups = [
  {
    heading: "Product",
    links: [
      { label: "Features", href: "#" },
      { label: "Pricing", href: "#" },
      { label: "Documentation", href: "#" },
    ],
  },
  {
    heading: "Company",
    links: [
      { label: "About", href: "#" },
      { label: "Blog", href: "#" },
      { label: "Careers", href: "#" },
    ],
  },
  {
    heading: "Legal",
    links: [
      { label: "Privacy", href: "#" },
      { label: "Terms", href: "#" },
      { label: "Security", href: "#" },
    ],
  },
];

export function Footer() {
  return (
    <footer className="bg-olive-950">
      <div className="mx-auto max-w-7xl px-6 py-12">
        {/* Top row */}
        <div className="flex flex-col gap-10 lg:flex-row lg:justify-between">
          {/* Logo */}
          <div>
            <span className="font-display text-xl text-white">DocTeams</span>
          </div>

          {/* Link groups */}
          <div className="grid grid-cols-2 gap-8 md:grid-cols-3">
            {linkGroups.map((group) => (
              <div key={group.heading}>
                <h3 className="mb-3 text-sm font-medium text-white">
                  {group.heading}
                </h3>
                <ul className="space-y-2">
                  {group.links.map((link) => (
                    <li key={link.label}>
                      <Link
                        href={link.href}
                        className="text-sm text-white/60 transition-colors hover:text-white"
                      >
                        {link.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>

        {/* Bottom row */}
        <div className="mt-8 border-t border-white/10 pt-8">
          <p className="text-sm text-white/40">
            &copy; {new Date().getFullYear()} DocTeams. All rights reserved.
          </p>
        </div>
      </div>
    </footer>
  );
}
