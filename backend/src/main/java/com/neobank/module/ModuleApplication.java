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
 * <p>{@code @EnableScheduling} powers two things: UC 07's mock playing the customer on a delay
 * ({@code InMemoryEsignMockClient}, via the {@code TaskScheduler} bean in {@code AppConfig}) and
 * the expiry sweep ({@code ExpiryClockService}).</p>
 */
@SpringBootApplication
@EnableScheduling
public class ModuleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModuleApplication.class, args);
    }
}
