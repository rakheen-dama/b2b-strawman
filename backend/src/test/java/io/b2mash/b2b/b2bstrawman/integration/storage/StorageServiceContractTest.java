package io.b2mash.b2b.b2bstrawman.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class StorageServiceContractTest {

  @Test
  void interface_declares_upload_bytes_method() throws NoSuchMethodException {
    Method method =
        StorageService.class.getMethod("upload", String.class, byte[].class, String.class);
    assertThat(method.getReturnType()).isEqualTo(String.class);
  }

  @Test
  void interface_declares_upload_stream_method() throws NoSuchMethodException {
    Method method =
        StorageService.class.getMethod(
            "upload", String.class, InputStream.class, long.class, String.class);
    assertThat(method.getReturnType()).isEqualTo(String.class);
  }

  @Test
  void interface_declares_download_method() throws NoSuchMethodException {
    Method method = StorageService.class.getMethod("download", String.class);
    assertThat(method.getReturnType()).isEqualTo(byte[].class);
  }

  @Test
  void interface_declares_delete_method() throws NoSuchMethodException {
    Method method = StorageService.class.getMethod("delete", String.class);
    assertThat(method.getReturnType()).isEqualTo(void.class);
  }

  @Test
  void interface_declares_generateUploadUrl_method() throws NoSuchMethodException {
    Method method =
        StorageService.class.getMethod(
            "generateUploadUrl", String.class, String.class, Duration.class);
    assertThat(method.getReturnType()).isEqualTo(PresignedUrl.class);
  }

  @Test
  void interface_declares_generateDownloadUrl_method() throws NoSuchMethodException {
    Method method =
        StorageService.class.getMethod("generateDownloadUrl", String.class, Duration.class);
    assertThat(method.getReturnType()).isEqualTo(PresignedUrl.class);
  }

  @Test
  void interface_has_exactly_six_methods() {
    // StorageService should declare exactly 6 methods (no more, no less)
    List<Method> declaredMethods =
        Arrays.stream(StorageService.class.getDeclaredMethods())
            .filter(m -> !m.isDefault() && !m.isSynthetic())
            .toList();
    assertThat(declaredMethods).hasSize(6);
  }

  @Test
  void presignedUrl_record_has_url_and_expiresAt() {
    var presigned =
        new PresignedUrl("https://example.com/file", java.time.Instant.now().plusSeconds(3600));
    assertThat(presigned.url()).isEqualTo("https://example.com/file");
    assertThat(presigned.expiresAt()).isNotNull();
  }
}
