package io.b2mash.b2b.b2bstrawman.correspondence.dto;

import java.util.UUID;

/** Result of filing a correspondence: the id, plus whether the file was a no-op (idempotent). */
public record FileCorrespondenceResult(UUID correspondenceId, boolean idempotent) {

  public static FileCorrespondenceResult created(UUID id) {
    return new FileCorrespondenceResult(id, false);
  }

  public static FileCorrespondenceResult idempotent(UUID id) {
    return new FileCorrespondenceResult(id, true);
  }
}
