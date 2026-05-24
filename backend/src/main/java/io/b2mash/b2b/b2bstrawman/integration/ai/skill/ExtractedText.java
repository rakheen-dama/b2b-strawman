package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

public record ExtractedText(
    String content, int characterCount, boolean wasTruncated, String truncationWarning) {}
