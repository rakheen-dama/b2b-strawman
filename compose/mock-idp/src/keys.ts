import crypto from "crypto";

const { publicKey, privateKey } = crypto.generateKeyPairSync("rsa", {
  modulusLength: 2048,
});

const jwk = publicKey.export({ format: "jwk" });

export const rsaPrivateKey = privateKey;

export const publicKeyJwk = {
  ...jwk,
  kid: "mock-key-1",
  use: "sig" as const,
  alg: "RS256",
};
