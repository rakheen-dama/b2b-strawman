package io.b2mash.b2b.b2bstrawman.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTagRequest(
    @NotBlank @Size(max = 50) String name, @Size(max = 7) String color) {}
