"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { TAB_UNDERLINE_CURRENT } from "@/components/shared/tab-underline";

const LINKS = [
  { href: "/platform-admin/access-requests", label: "Access Requests" },
  { href: "/platform-admin/billing", label: "Billing" },
  { href: "/platform-admin/demo", label: "Demo" },
] as const;

/**
 * The platform-admin bar (K-98): the current page's link carries `aria-current="page"` and the
 * shared underline. A client module only because the layout cannot read the pathname. Platform
 * admin is always light (outside `/org/`), so the bar keeps its light classes; `px-4` + the links'
 * `px-2` keep the first label at today's 24px inset, `pt-1` today's 45px bar height.
 */
export function PlatformAdminNav() {
  const pathname = usePathname();
  return (
    <nav className="flex gap-2 border-b border-slate-200 bg-white px-4 pt-1">
      {LINKS.map(({ href, label }) => (
        <Link
          key={href}
          href={href}
          aria-current={pathname?.startsWith(href) ? "page" : undefined}
          className={TAB_UNDERLINE_CURRENT}
        >
          {label}
        </Link>
      ))}
    </nav>
  );
}
