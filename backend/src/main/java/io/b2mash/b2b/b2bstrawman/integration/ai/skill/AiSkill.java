package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecution;
import io.b2mash.b2b.b2bstrawman.integration.ai.gate.AiExecutionGate;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import java.util.List;

/**
 * Contract for AI skills. Each skill assembles its own prompts and parses its own output into
 * execution gates. The shared orchestration is in AiSkillExecutionService.
 */
public interface AiSkill {

  /** Unique skill identifier (e.g., "fica-verification", "matter-intake"). */
  String skillId();

  /** Assemble the system prompt using the firm's profile. */
  String assembleSystemPrompt(AiFirmProfile profile);

  /** Assemble the user prompt from the execution context. */
  String assembleUserPrompt(SkillContext context);

  /** Parse AI output and create execution gates (proposed actions needing attorney review). */
  List<AiExecutionGate> createGates(AiExecution execution, String outputContent);

  /** Whether this skill requires vision (image) inputs. */
  boolean requiresVision();
}
