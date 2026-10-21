"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { SearchInput } from "@/components/views/SearchInput";

const DEBOUNCE_MS = 300;

/**
 * Toolbar search for the Matters table. Writes `?search=` (debounced, other params kept); the
 * server component filters on name, reference and client (F-2 — the backend has no search param).
 */
export function MatterSearch() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const urlValue = searchParams.get("search") ?? "";
  const [value, setValue] = useState(urlValue);
  const lastPushed = useRef(urlValue);

  // Follow external URL changes (back/forward, "Clear filters").
  useEffect(() => {
    if (urlValue !== lastPushed.current) {
      lastPushed.current = urlValue;
      setValue(urlValue);
    }
  }, [urlValue]);

  useEffect(() => {
    const next = value.trim();
    if (next === lastPushed.current.trim()) return;
    const timer = setTimeout(() => {
      const params = new URLSearchParams(searchParams.toString());
      if (next) params.set("search", next);
      else params.delete("search");
      lastPushed.current = next;
      const qs = params.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname);
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [value, pathname, router, searchParams]);

  return (
    <div className="w-full sm:w-64" role="search" aria-label="Search matters">
      <SearchInput value={value} onChange={setValue} placeholder="Search matters…" />
    </div>
  );
}
