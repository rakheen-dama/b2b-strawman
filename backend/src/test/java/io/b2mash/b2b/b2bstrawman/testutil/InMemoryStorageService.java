package io.b2mash.b2b.b2bstrawman.testutil;

import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory StorageService for tests. Replaces LocalStack S3 for all tests except
 * S3PresignedUrlServiceTest which needs real presigned URL HTTP round-trips.
 */
public class InMemoryStorageService implements StorageService {

  private final ConcurrentHashMap<String, byte[]> store = new ConcurrentHashMap<>();

  @Override
  public String upload(String key, byte[] content, String contentType) {
    store.put(key, content);
    return key;
  }

  @Override
  public String upload(String key, InputStream content, long contentLength, String contentType) {
    try {
      store.put(key, content.readAllBytes());
    } catch (IOException e) {
      throw new RuntimeException("Failed to read input stream", e);
    }
    return key;
  }

  @Override
  public byte[] download(String key) {
    byte[] data = store.get(key);
    if (data == null) {
      throw new RuntimeException("Key not found: " + key);
    }
    return data;
  }

  @Override
  public void delete(String key) {
    store.remove(key);
  }

  @Override
  public PresignedUrl generateUploadUrl(String key, String contentType, Duration expiry) {
    return new PresignedUrl("http://test-storage/upload/" + key, Instant.now().plus(expiry));
  }

  @Override
  public PresignedUrl generateDownloadUrl(String key, Duration expiry) {
    return new PresignedUrl("http://test-storage/download/" + key, Instant.now().plus(expiry));
  }

  @Override
  public List<String> listKeys(String prefix) {
    return store.keySet().stream().filter(k -> k.startsWith(prefix)).toList();
  }
}
