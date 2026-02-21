import express from "express";
import jwt from "jsonwebtoken";
import { rsaPrivateKey, publicKeyJwk } from "./keys";
import { USERS } from "./users";

const app = express();
app.use(express.json());

const PORT = parseInt(process.env.PORT || "8090", 10);

// GET /.well-known/jwks.json — JWKS endpoint
app.get("/.well-known/jwks.json", (_req, res) => {
  res.json({ keys: [publicKeyJwk] });
});

// POST /token — Issue a Clerk v2 format JWT
app.post("/token", (req, res) => {
  const { userId, orgId, orgSlug, orgRole } = req.body;

  if (!userId || !orgId || !orgSlug || !orgRole) {
    res.status(400).json({
      error: "Missing required fields: userId, orgId, orgSlug, orgRole",
    });
    return;
  }

  const now = Math.floor(Date.now() / 1000);
  const payload = {
    sub: userId,
    iss: "http://mock-idp:8090",
    aud: "docteams-e2e",
    iat: now,
    exp: now + 86400,
    v: 2,
    o: {
      id: orgId,
      rol: orgRole,
      slg: orgSlug,
    },
  };

  const privateKeyPem = rsaPrivateKey.export({ type: "pkcs8", format: "pem" });
  const token = jwt.sign(payload, privateKeyPem, {
    algorithm: "RS256",
    keyid: "mock-key-1",
  });

  res.json({
    access_token: token,
    token_type: "Bearer",
    expires_in: 86400,
  });
});

// GET /userinfo/:userId — User profile lookup
app.get("/userinfo/:userId", (req, res) => {
  const user = USERS[req.params.userId];
  if (!user) {
    res.status(404).json({ error: "User not found" });
    return;
  }

  res.json({
    id: user.id,
    firstName: user.firstName,
    lastName: user.lastName,
    email: user.email,
    imageUrl: user.imageUrl,
  });
});

app.listen(PORT, () => {
  console.log(`Mock IDP running on port ${PORT}`);
  console.log(`JWKS: http://localhost:${PORT}/.well-known/jwks.json`);
  console.log(`Token: POST http://localhost:${PORT}/token`);
  console.log(`Users: ${Object.keys(USERS).join(", ")}`);
});
