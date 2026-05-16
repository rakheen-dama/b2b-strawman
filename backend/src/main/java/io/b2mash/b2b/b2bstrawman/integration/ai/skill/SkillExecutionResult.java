package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import java.util.List;

/** Result of a skill execution, bundling the execution record with any gates created. */
public record SkillExecutionResult(AiExecution execution, List<AiExecutionGate> gates) {}
