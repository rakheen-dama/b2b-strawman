package io.b2mash.b2b.b2bstrawman.integration.secret;

/**
 * Abstraction for storing and retrieving sensitive credentials. Queries execute within the current
 * tenant's schema.
 */
public interface SecretStore {

  /** Store a secret (encrypts before persistence). Overwrites if key exists. */
  void store(String secretKey, String plaintext);

  /** Retrieve a secret (decrypts after read). Throws if not found. */
  String retrieve(String secretKey);

  /** Delete a secret. No-op if not found. */
  void delete(String secretKey);

  /** Check whether a secret exists for the given key. */
  boolean exists(String secretKey);
}
