package io.b2mash.b2b.b2bstrawman.integration.secret;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EncryptedDatabaseSecretStore implements SecretStore {

  private static final Logger log = LoggerFactory.getLogger(EncryptedDatabaseSecretStore.class);

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_TAG_LENGTH = 128; // bits
  private static final int IV_LENGTH = 12; // bytes (96 bits)

  private final OrgSecretRepository repository;
  private final SecretKeySpec encryptionKey;
  private final SecureRandom secureRandom = new SecureRandom();

  public EncryptedDatabaseSecretStore(
      OrgSecretRepository repository, @Value("${integration.encryption-key:}") String encodedKey) {
    this.repository = repository;
    if (encodedKey == null || encodedKey.isBlank()) {
      this.encryptionKey = null; // Will fail at @PostConstruct
    } else {
      byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
      this.encryptionKey = new SecretKeySpec(keyBytes, "AES");
    }
  }

  @PostConstruct
  void validateKey() {
    if (encryptionKey == null) {
      throw new IllegalStateException(
          "INTEGRATION_ENCRYPTION_KEY environment variable is not set. "
              + "Cannot start without encryption key for secret storage.");
    }
    if (encryptionKey.getEncoded().length != 32) {
      throw new IllegalStateException(
          "INTEGRATION_ENCRYPTION_KEY must be a Base64-encoded 256-bit (32-byte) key. "
              + "Got "
              + encryptionKey.getEncoded().length
              + " bytes.");
    }
  }

  @Override
  @Transactional
  public void store(String key, String plaintext) {
    byte[] iv = generateIv();
    byte[] ciphertext = encrypt(plaintext.getBytes(StandardCharsets.UTF_8), iv);

    String encodedCiphertext = Base64.getEncoder().encodeToString(ciphertext);
    String encodedIv = Base64.getEncoder().encodeToString(iv);

    var existing = repository.findBySecretKey(key);
    if (existing.isPresent()) {
      existing.get().updateEncryptedValue(encodedCiphertext, encodedIv, 1);
      repository.save(existing.get());
    } else {
      var secret = new OrgSecret(key, encodedCiphertext, encodedIv, 1);
      repository.save(secret);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public String retrieve(String key) {
    var secret =
        repository
            .findBySecretKey(key)
            .orElseThrow(() -> new ResourceNotFoundException("Secret", key));

    byte[] ciphertext = Base64.getDecoder().decode(secret.getEncryptedValue());
    byte[] iv = Base64.getDecoder().decode(secret.getIv());

    byte[] plaintext = decrypt(ciphertext, iv);
    return new String(plaintext, StandardCharsets.UTF_8);
  }

  @Override
  @Transactional
  public void delete(String key) {
    repository.deleteBySecretKey(key);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean exists(String key) {
    return repository.existsBySecretKey(key);
  }

  private byte[] generateIv() {
    byte[] iv = new byte[IV_LENGTH];
    secureRandom.nextBytes(iv);
    return iv;
  }

  private byte[] encrypt(byte[] plaintext, byte[] iv) {
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      return cipher.doFinal(plaintext);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  private byte[] decrypt(byte[] ciphertext, byte[] iv) {
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      return cipher.doFinal(ciphertext);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Decryption failed", e);
    }
  }
}
