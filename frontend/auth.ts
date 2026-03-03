import NextAuth from "next-auth";
import Keycloak from "next-auth/providers/keycloak";

/**
 * Refresh the access token via Keycloak's token endpoint.
 * Returns the new token fields on success, or an error flag on failure.
 */
async function refreshAccessToken(refreshToken: string): Promise<{
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
} | null> {
  const issuer = process.env.KEYCLOAK_ISSUER;
  const clientId = process.env.KEYCLOAK_CLIENT_ID;
  const clientSecret = process.env.KEYCLOAK_CLIENT_SECRET;

  if (!issuer || !clientId || !clientSecret) {
    console.error("[auth] Missing Keycloak env vars for token refresh");
    return null;
  }

  // Keycloak token endpoint follows OIDC convention: {issuer}/protocol/openid-connect/token
  const tokenUrl = `${issuer}/protocol/openid-connect/token`;

  try {
    const response = await fetch(tokenUrl, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        client_id: clientId,
        client_secret: clientSecret,
        refresh_token: refreshToken,
      }),
    });

    if (!response.ok) {
      console.error(
        `[auth] Token refresh failed: ${response.status} ${response.statusText}`,
      );
      return null;
    }

    const data = await response.json();
    return {
      accessToken: data.access_token as string,
      refreshToken: data.refresh_token as string,
      expiresAt: Math.floor(Date.now() / 1000) + (data.expires_in as number),
    };
  } catch (error) {
    console.error("[auth] Token refresh error:", error);
    return null;
  }
}

export const { handlers, auth, signIn, signOut } = NextAuth({
  providers: [
    Keycloak({
      clientId: process.env.KEYCLOAK_CLIENT_ID,
      clientSecret: process.env.KEYCLOAK_CLIENT_SECRET,
      issuer: process.env.KEYCLOAK_ISSUER,
    }),
  ],
  session: {
    strategy: "jwt",
  },
  pages: {
    signIn: "/sign-in",
  },
  callbacks: {
    async jwt({ token, account }) {
      // On initial sign-in, persist the OIDC tokens with validation
      if (account) {
        if (
          !account.access_token ||
          !account.refresh_token ||
          !account.expires_at
        ) {
          console.error(
            "[auth] Missing token fields from Keycloak on sign-in",
          );
          token.error = "missing_tokens";
          return token;
        }
        token.accessToken = account.access_token;
        token.refreshToken = account.refresh_token;
        token.expiresAt = account.expires_at;
        delete token.error;
        return token;
      }

      // Not expired yet (with 60s buffer) — return existing token
      if (token.expiresAt && Date.now() < (token.expiresAt - 60) * 1000) {
        return token;
      }

      // Token expired — attempt refresh
      if (!token.refreshToken) {
        console.error("[auth] No refresh token available for token refresh");
        token.error = "no_refresh_token";
        return token;
      }

      const refreshed = await refreshAccessToken(token.refreshToken);
      if (!refreshed) {
        token.error = "refresh_failed";
        return token;
      }

      token.accessToken = refreshed.accessToken;
      token.refreshToken = refreshed.refreshToken;
      token.expiresAt = refreshed.expiresAt;
      delete token.error;
      return token;
    },
    async session({ session, token }) {
      // Expose access token and error state to the session
      session.accessToken = token.accessToken;
      if (token.error) {
        session.error = token.error;
      }
      return session;
    },
  },
});
