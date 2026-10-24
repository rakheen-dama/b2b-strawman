package io.b2mash.b2b.b2bstrawman.acceptance;

import jakarta.validation.constraints.Size;

/** Optional reason supplied by the recipient when declining a document (Phase 88 U23). */
public record DeclineSubmission(@Size(max = 2000) String reason) {}
