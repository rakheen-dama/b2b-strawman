package io.b2mash.b2b.b2bstrawman.integration.ai.anthropic;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables binding of {@link AnthropicProperties} from application configuration. */
@Configuration
@EnableConfigurationProperties(AnthropicProperties.class)
class AnthropicConfig {}
