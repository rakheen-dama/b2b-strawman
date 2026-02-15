import "server-only";
import { auth } from "@clerk/nextjs/server";
import { NextResponse } from "next/server";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8080";

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const { id } = await params;

  if (!UUID_RE.test(id)) {
    return new NextResponse("Bad Request", { status: 400 });
  }

  const { getToken, orgRole } = await auth();

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return new NextResponse("Forbidden", { status: 403 });
  }

  const token = await getToken();
  if (!token) {
    return new NextResponse("Unauthorized", { status: 401 });
  }

  const response = await fetch(`${BACKEND_URL}/api/invoices/${id}/preview`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
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
