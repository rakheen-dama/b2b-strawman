/**
 * DEV-ONLY: Programmatic Keycloak login helper for browser automation.
 * Performs the full OAuth2 flow server-side and sets the gateway SESSION cookie.
 *
 * Usage: GET /api/dev-login?user=bob
 * Users: thandi, bob, carol
 *
 * DELETE THIS FILE before production deployment.
 */
import { NextRequest, NextResponse } from "next/server";

const USERS: Record<string, { email: string; password: string }> = {
  thandi: { email: "thandi@thornton-test.local", password: "SecureP@ss1" },
  bob: { email: "bob@thornton-test.local", password: "SecureP@ss2" },
  carol: { email: "carol@thornton-test.local", password: "SecureP@ss3" },
  padmin: { email: "padmin@docteams.local", password: "password" },
};

export async function GET(request: NextRequest) {
  const user = request.nextUrl.searchParams.get("user");
  if (!user || !USERS[user]) {
    return NextResponse.json(
      { error: "Unknown user. Use ?user=thandi|bob|carol|padmin" },
      { status: 400 }
    );
  }

  const { email, password } = USERS[user];
  const gatewayUrl = process.env.GATEWAY_URL || "http://localhost:8443";

  try {
    // Step 1: Hit gateway OAuth2 authorization endpoint
    const authResp = await fetch(
      `${gatewayUrl}/oauth2/authorization/keycloak`,
      { redirect: "manual" }
    );
    const kcUrl = authResp.headers.get("location");
    if (!kcUrl) throw new Error("No redirect from gateway");

    // Capture gateway session cookie
    const gatewaySetCookie = authResp.headers.get("set-cookie") || "";
    const sessionMatch = gatewaySetCookie.match(/SESSION=([^;]+)/);
    let sessionCookie = sessionMatch ? `SESSION=${sessionMatch[1]}` : "";

    // Step 2: GET Keycloak login page
    const kcResp = await fetch(kcUrl, {
      redirect: "manual",
      headers: sessionCookie ? { Cookie: sessionCookie } : {},
    });
    const kcPage = await kcResp.text();

    // Extract KC cookies
    const kcCookies: string[] = [];
    kcResp.headers.forEach((value, key) => {
      if (key.toLowerCase() === "set-cookie") {
        const cookiePart = value.split(";")[0];
        kcCookies.push(cookiePart);
      }
    });
    const kcCookieStr = kcCookies.join("; ");

    // Extract loginAction from the page
    const loginActionMatch = kcPage.match(/"loginAction":\s*"([^"]+)"/);
    if (!loginActionMatch) throw new Error("No loginAction found on KC page");
    const loginAction = loginActionMatch[1];

    // Step 3: POST username (first step of two-step flow)
    const usernameResp = await fetch(loginAction, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        Cookie: kcCookieStr,
      },
      body: `username=${encodeURIComponent(email)}`,
      redirect: "manual",
    });

    let passwordAction: string;
    if (usernameResp.status === 302) {
      // Single-step flow — already redirecting
      const callbackUrl = usernameResp.headers.get("location");
      if (!callbackUrl) throw new Error("No redirect after username");
      // This shouldn't happen in two-step flow, but handle it
      passwordAction = "";
    } else {
      // Two-step flow — need to extract second loginAction
      const usernameResponsePage = await usernameResp.text();

      // Update KC cookies from response
      usernameResp.headers.forEach((value, key) => {
        if (key.toLowerCase() === "set-cookie") {
          const cookiePart = value.split(";")[0];
          const cookieName = cookiePart.split("=")[0];
          // Replace existing cookie or add new one
          const idx = kcCookies.findIndex((c) => c.startsWith(cookieName + "="));
          if (idx >= 0) kcCookies[idx] = cookiePart;
          else kcCookies.push(cookiePart);
        }
      });

      const action2Match = usernameResponsePage.match(/"loginAction":\s*"([^"]+)"/);
      if (!action2Match) throw new Error("No loginAction found on password page");
      passwordAction = action2Match[1];
    }

    // Step 4: POST password
    const updatedKcCookieStr = kcCookies.join("; ");
    const passwordResp = await fetch(passwordAction, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        Cookie: updatedKcCookieStr,
      },
      body: `username=${encodeURIComponent(email)}&password=${encodeURIComponent(password)}`,
      redirect: "manual",
    });

    if (passwordResp.status !== 302) {
      const body = await passwordResp.text();
      throw new Error(`Password step failed (${passwordResp.status}): ${body.substring(0, 200)}`);
    }

    const callbackUrl = passwordResp.headers.get("location");
    if (!callbackUrl) throw new Error("No redirect after password");

    // Step 5: Follow callback to gateway (code exchange)
    const callbackResp = await fetch(callbackUrl, {
      redirect: "manual",
      headers: { Cookie: sessionCookie },
    });

    if (callbackResp.status !== 302) {
      throw new Error(`Gateway callback failed (${callbackResp.status})`);
    }

    // Extract the new SESSION cookie from gateway
    let newSessionCookie = "";
    callbackResp.headers.forEach((value, key) => {
      if (key.toLowerCase() === "set-cookie" && value.includes("SESSION=")) {
        const match = value.match(/SESSION=([^;]+)/);
        if (match) newSessionCookie = match[1];
      }
    });

    if (!newSessionCookie) throw new Error("No SESSION cookie from gateway callback");

    // Step 6: Redirect browser to dashboard with the gateway SESSION cookie
    const response = NextResponse.redirect(new URL("/dashboard", request.url));

    // Set the gateway SESSION cookie on the browser for localhost:8443
    // The browser needs this cookie for subsequent requests to the gateway
    response.cookies.set("SESSION", newSessionCookie, {
      path: "/",
      httpOnly: true,
      sameSite: "lax",
      domain: "localhost",
    });

    return response;
  } catch (error) {
    return NextResponse.json(
      { error: String(error) },
      { status: 500 }
    );
  }
}
