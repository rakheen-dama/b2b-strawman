package io.b2mash.b2b.b2bstrawman.fielddefinition.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PatchFieldDefinitionRequest(@NotNull List<String> requiredForContexts) {}
