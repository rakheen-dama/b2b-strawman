import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

/**
 * Portal middleware — currently only redirects `/` → `/home`.
 * Auth gating lives in `(authenticated)/layout.tsx` (client-side, localStorage-backed JWT).
 */
export function middleware(request: NextRequest) {
  if (request.nextUrl.pathname === "/") {
    const url = request.nextUrl.clone();
    url.pathname = "/home";
    return NextResponse.redirect(url);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/"],
};
