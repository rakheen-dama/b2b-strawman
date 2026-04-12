import "server-only";
import { API_BASE, getAuthFetchOptions } from "@/lib/api/client";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { NextResponse } from "next/server";

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function GET(_request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  if (!UUID_RE.test(id)) {
    return new NextResponse("Bad Request", { status: 400 });
  }

  let caps;
  try {
    caps = await fetchMyCapabilities();
  } catch {
    return new NextResponse("Unauthorized", { status: 401 });
  }

  if (!caps.isAdmin && !caps.isOwner) {
    return new NextResponse("Forbidden", { status: 403 });
  }

  let authOptions: { headers: Record<string, string>; credentials?: RequestCredentials };
  try {
    authOptions = await getAuthFetchOptions("GET");
  } catch {
    return new NextResponse("Unauthorized", { status: 401 });
  }

  const response = await fetch(`${API_BASE}/api/invoices/${id}/preview`, {
    headers: authOptions.headers,
    credentials: authOptions.credentials,
  });

  if (!response.ok) {
    return new NextResponse(response.statusText, { status: response.status });
  }

  const html = await response.text();
  return new NextResponse(html, {
    status: 200,
    headers: { "Content-Type": "text/html" },
  });
}
