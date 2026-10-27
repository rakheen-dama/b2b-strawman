package io.b2mash.b2b.b2bstrawman.template;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * The {@code org.logoUrl} context value for a PNG/JPEG branding logo (Phase 88 626D, ruling R-1):
 * the presigned URL plus a lazily built {@code data:image/png|jpeg;base64,} URI of the stored
 * bytes. The bytes are downloaded only when an {@code image}-hinted variable actually renders the
 * logo ({@link VariableFormatter}), at most once per value — building a context (readiness checks,
 * required-field validation, DOCX merge) never touches storage. Values live for one context build,
 * so a re-uploaded logo is never served stale.
 *
 * <p>{@link #toString()} is the presigned URL, so every non-image consumer (conditional blocks,
 * DOCX merge, unhinted rendering, required-field checks) sees exactly the pre-626D string.
 */
public final class InlineLogo {

  private final String presignedUrl;
  private final Supplier<String> dataUriLoader;
  private boolean loaded;
  private String dataUri;

  /**
   * @param presignedUrl the fallback URL (never null)
   * @param dataUriLoader builds the {@code data:} URI; returns {@code null} when the bytes are
   *     unavailable (it must not throw)
   */
  InlineLogo(String presignedUrl, Supplier<String> dataUriLoader) {
    this.presignedUrl = Objects.requireNonNull(presignedUrl, "presignedUrl");
    this.dataUriLoader = Objects.requireNonNull(dataUriLoader, "dataUriLoader");
  }

  /** The presigned URL (the fallback {@code src}). */
  public String presignedUrl() {
    return presignedUrl;
  }

  /**
   * The {@code <img>} source: the {@code data:} URI of the logo bytes (loaded on first call, then
   * memoized), or the presigned URL when the bytes are unavailable.
   */
  public synchronized String src() {
    if (!loaded) {
      dataUri = dataUriLoader.get();
      loaded = true;
    }
    return dataUri != null ? dataUri : presignedUrl;
  }

  @Override
  public String toString() {
    return presignedUrl;
  }
}
