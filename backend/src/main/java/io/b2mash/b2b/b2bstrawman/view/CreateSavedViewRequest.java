package io.b2mash.b2b.b2bstrawman.view;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateSavedViewRequest(
    @NotBlank @Size(max = 20) String entityType,
    @NotBlank @Size(max = 100) String name,
    @NotNull Map<String, Object> filters,
    List<String> columns,
    boolean shared,
    int sortOrder) {}
