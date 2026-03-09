package io.b2mash.b2b.b2bstrawman.onboarding;

import java.util.List;

public record OnboardingProgressResponse(
    List<OnboardingStep> steps, boolean dismissed, int completedCount, int totalCount) {}
