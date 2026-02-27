package io.b2mash.b2b.b2bstrawman.acceptance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Submission data provided by the acceptor when accepting a document. */
public record AcceptanceSubmission(@NotBlank @Size(min = 2, max = 255) String name) {}
