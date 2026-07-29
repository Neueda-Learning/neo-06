package com.neobank.module.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestClient;

/**
 * Infrastructure beans. The HTTP client this module calls the orchestrator back with, and the
 * scheduler UC 07's mock uses to post a delayed auto-outcome.
 *
 * <p>The thread pool the decision runs on is Spring Boot's own
 * {@code applicationTaskExecutor} — no bean needed here. Size and naming are properties:
 * {@code spring.task.execution.*} in {@code application.yml}.</p>
 */
@Configuration
public class AppConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

    /**
     * A small dedicated pool for one-shot, timed work — today that is only
     * {@code InMemoryEsignMockClient}'s {@code EsignMode#DELAYED}/{@code INSTANT} auto-post.
     * Kept separate from {@code applicationTaskExecutor} (which runs the potentially slower
     * {@code decide()} rules) so a burst of scheduled sign/decline posts can never starve a
     * pending {@code /execute} of a worker thread, or vice versa.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("esign-mock-");
        scheduler.initialize();
        return scheduler;
    }
}
