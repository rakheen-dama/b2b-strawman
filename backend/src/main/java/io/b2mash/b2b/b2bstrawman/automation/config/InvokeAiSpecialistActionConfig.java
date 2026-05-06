package io.b2mash.b2b.b2bstrawman.automation.config;

import java.util.Map;

public record InvokeAiSpecialistActionConfig(
    String specialistId,
    Map<String, String> contextRef,
    String initialPrompt,
    String lookback,
    String mode,
    int timeoutSeconds)
    implements ActionConfig {}
