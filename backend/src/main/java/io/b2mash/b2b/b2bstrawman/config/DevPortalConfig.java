package io.b2mash.b2b.b2bstrawman.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuration for the dev portal Thymeleaf harness. Activated only under "local" or "dev"
 * profiles per ADR-033. Spring Boot auto-configures Thymeleaf template resolution when
 * spring-boot-starter-thymeleaf is on the classpath, so no explicit bean definitions are needed.
 */
@Configuration
@Profile({"local", "dev"})
public class DevPortalConfig {}
