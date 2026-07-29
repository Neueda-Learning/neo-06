package com.neobank.module;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * One module of the neo-bank onboarding journey.
 *
 * <p>It accepts an application from the orchestrator with {@code 202}, runs
 * {@link com.neobank.module.service.DecisionRules} off the request thread, and calls the
 * orchestrator back with {@code ACCEPTED} / {@code REJECTED} / {@code REFERRED}.</p>
 *
 * <p>Which module this is — its id, display name and BIAN domain — is configuration, not
 * code: see {@code application.yml} and {@code .env.example}.</p>
 *
 * <p>{@code @EnableScheduling} backs UC 04's expiry clock (see
 * {@link com.neobank.module.service.ExpiryClockService}) — Spring Boot auto-configures its own
 * default {@code TaskScheduler} for this, entirely separate from {@code applicationTaskExecutor}
 * (see {@code AppConfig}'s and {@code DefaultEsignEventScheduler}'s javadoc for why that one is
 * never exposed as an {@code Executor} bean).</p>
 */
@SpringBootApplication
@EnableScheduling
public class ModuleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModuleApplication.class, args);
    }
}

