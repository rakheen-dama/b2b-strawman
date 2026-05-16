package io.b2mash.b2b.b2bstrawman.testutil;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestAiConfiguration {

  @Bean
  public StubAiProvider stubAiProvider() {
    return new StubAiProvider();
  }
}
