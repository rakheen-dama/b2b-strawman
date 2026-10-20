import "server-only";
import { API_BASE, getAuthFetchOptions } from "@/lib/api/client";
import { CLIENTS_PATH } from "@/lib/api/clients";
import { NextResponse } from "next/server";

/**
 * BFF proxy for GET /api/customers.
 *
 * Forwards browser-origin `GET /api/customers[?<query>]` to the API
 * gateway (or backend in mock mode). Required because some client-side
 * flows (combobox fetches, ad-hoc prefetch, QA harnesses) hit
 * `/api/customers` on the Next.js origin (port 3000) instead of the
 * gateway (port 8443). Without this route, Next.js would respond 404.
 *
 * Upstream target is the Clients collection (`CLIENTS_PATH` from `lib/api/clients.ts`;
 * Phase 87 S2/S4, ADR-337). It is a raw pass-through (status, body and content-type
 * verbatim), so it uses the path constant rather than `listClients()`, which parses and
 * throws. This Next.js route file itself stays at `app/api/customers/route.ts` until 610D
 * renames it.
 *
 * Server components and actions should call the `lib/api/clients.ts` functions
 * (e.g. `listClients()`) — they route through the gateway on their own. This handler
 * exists purely so browser fetches do not 404.
 *
 * Fix for GAP-L-48.
 */
export async function GET(request: Request) {
  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("GET");
  } catch (err: unknown) {
    const status =
      err != null && typeof err === "object" && "status" in err
        ? (err as { status: number }).status
        : 0;
    if (status === 401 || status === 403) {
      return new NextResponse("Unauthorized", { status: 401 });
    }
    return new NextResponse("Upstream Error", { status: 502 });
  }

  // Preserve query string so callers can pass ?size=200, ?view=<uuid>,
  // ?lifecycleStatus=ACTIVE, custom field filters, tag filters, etc.
  const url = new URL(request.url);
  const qs = url.search; // includes leading "?" when present

  const upstream = await fetch(`${API_BASE}${CLIENTS_PATH}${qs}`, {
    method: "GET",
    headers: authOptions.headers,
    credentials: authOptions.credentials,
  });

  const body = await upstream.text();
  const contentType = upstream.headers.get("content-type") ?? "application/json";

  return new NextResponse(body, {
    status: upstream.status,
    headers: { "Content-Type": contentType },
  });
}
