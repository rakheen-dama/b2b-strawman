package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JobQueueProperties.class)
public class JobQueueConfig {}
