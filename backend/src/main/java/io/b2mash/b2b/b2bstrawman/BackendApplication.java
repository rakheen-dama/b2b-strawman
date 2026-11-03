package io.b2mash.b2b.b2bstrawman;

import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class BackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }

  /** Server "today" is South African time regardless of the JVM zone (G88-F48). */
  @Bean
  public Clock clock() {
    return Clock.system(KaziTime.ZONE);
  }
}
