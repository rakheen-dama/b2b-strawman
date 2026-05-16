package io.b2mash.b2b.b2bstrawman.testutil;

import io.b2mash.b2b.b2bstrawman.integration.ai.AiProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestAiConfiguration {

  @Bean
  @Primary
  public AiProvider stubAiProvider() {
    return new StubAiProvider();
  }
}
