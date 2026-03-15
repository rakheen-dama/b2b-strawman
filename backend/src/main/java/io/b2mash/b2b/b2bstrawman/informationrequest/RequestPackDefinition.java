package io.b2mash.b2b.b2bstrawman.informationrequest;

import java.util.List;

/** DTO record for deserializing request pack JSON files from the classpath. */
public record RequestPackDefinition(
    String packId,
    int version,
    String verticalProfile,
    String name,
    String description,
    List<RequestPackItemDefinition> items) {

  public record RequestPackItemDefinition(
      String name,
      String description,
      String responseType,
      boolean required,
      String fileTypeHints,
      int sortOrder) {}
}
